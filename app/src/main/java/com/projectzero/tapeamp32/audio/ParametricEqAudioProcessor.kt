package com.projectzero.tapeamp32.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.projectzero.tapeamp32.data.EqPreset
import com.projectzero.tapeamp32.data.flatTenBandPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Custom biquad IIR parametric EQ yang berjalan di dalam pipeline ExoPlayer AudioProcessor.
 * - Pemrosesan 32-bit float bit-perfect, biquad Direct Form II Transposed double-precision.
 * - Auto-preamp/Headroom management otomatis.
 * - Clean peak limiter: stereo-linked envelope follower + hard-knee gain reduction, TANPA
 *   waveshaping/saturasi (tidak pakai tanh atau fungsi non-linear apa pun) -- transparan
 *   sepenuhnya di bawah threshold, cuma menahan gain saat sinyal melewati ambang batas.
 */
@UnstableApi
class ParametricEqAudioProcessor : BaseAudioProcessor() {

    companion object {
        // BARU (v1.9): target RMS referensi untuk REPLAY GAIN, kira-kira setara
        // -18 dBFS RMS -- proksi kasar loudness rata-rata musik pop/rock modern.
        // Lihat catatan jujur di finishReplayGainMeasurement().
        private const val REPLAY_GAIN_TARGET_RMS_DB = -18.0
    }

    @Volatile var bypass: Boolean = false
    @Volatile var limiterEnabled: Boolean = true
    @Volatile var vuCallback: ((left: Float, right: Float) -> Unit)? = null

    // BARU (v1.6, "BIT-PERFECT MODE"): master override yang melewati SEMUA tahap
    // pemrosesan sinyal SOFTWARE sekaligus -- 10-band EQ, Vocal enhancer, Balance,
    // Stereo Expansion, Mono/Stereo, DAN Peak Limiter -- beda dari `bypass` (EQ
    // BYPASS) yang cuma mematikan EQ 10-band/Vocal dan SENGAJA membiarkan
    // Balance/Stereo Expansion/Mono tetap aktif (lihat komentar PASS 1b di
    // queueInput()).
    //
    // Toggle yang sama (lihat PlayerManager.setBitPerfectMode) JUGA meminta jalur
    // AUDIO OFFLOAD hardware ke ExoPlayer/AudioSink -- generik untuk device/USB DAC
    // apa pun, bukan hardcode merk tertentu. Kalau offload benar-benar didukung &
    // aktif untuk lagu yang sedang diputar, sample bahkan tidak akan pernah sampai
    // ke fungsi queueInput() ini sama sekali (dikirim langsung ke chip DSP hardware).
    // Flag bitPerfectMode di sini adalah JARING PENGAMAN yang selalu berlaku: kalau
    // device/format TIDAK mendukung offload dan ExoPlayer fallback ke jalur biasa,
    // sample tetap dijamin diteruskan apa adanya lewat gate di bawah -- bukan
    // diam-diam ke-skip pengamanannya.
    //
    // Individual toggle EQ Bypass/Vocal/Balance/Stereo Expansion/Mono/Limiter TIDAK
    // ikut diubah nilainya saat mode ini aktif -- cuma "dibekukan" sementara, jadi
    // begitu mode ini dimatikan lagi, semua kembali persis ke posisi knop terakhir.
    @Volatile var bitPerfectMode: Boolean = false

    // ================================================================
    // BARU (v1.9): REPLAY GAIN beneran -- lihat catatan panjang di changelog
    // v1.9 & PlayerViewModel (songKeyFor/applyReplayGainForSong) untuk alur
    // lengkapnya. Ringkas: gain (dB) berikut adalah GAIN AKHIR yang sudah
    // dihitung/di-cache per lagu (lihat finishReplayGainMeasurement), diterapkan
    // sebagai perkalian linear ke sample MENTAH sebelum EQ/Vocal (supaya hasil
    // normalisasi konsisten apa pun preset tone control yang sedang aktif) --
    // bukan sekadar geser slider volume ExoPlayer (player.volume) yang berlaku
    // sama untuk semua lagu.
    //
    // Sengaja TIDAK ikut digerbang oleh `bypass` (EQ BYPASS) -- ini normalisasi
    // level, bukan tone-shaping, sama seperti Balance/Stereo Expansion di PASS 1b.
    // TAPI wajib mati total saat bitPerfectMode aktif, karena bit-perfect artinya
    // sample tidak boleh diubah SAMA SEKALI, termasuk oleh gain "baik hati"
    // sekalipun.
    // ================================================================
    @Volatile var replayGainEnabled: Boolean = false
    @Volatile private var replayGainLinear: Double = 1.0

    fun setReplayGainDb(db: Double) {
        replayGainLinear = 10.0.pow(db.coerceIn(-12.0, 12.0) / 20.0)
    }

    // --- Pengukuran loudness untuk MENGHASILKAN gain di atas ---
    //
    // Diukur dari sample PCM MENTAH yang benar-benar mengalir lewat pipeline
    // decode ExoPlayer di queueInput() (PASS 1a, sebelum EQ/Vocal/gain apa pun
    // disentuh) -- bukan ditebak dari metadata file atau nilai placeholder, dan
    // independen dari preset EQ yang sedang aktif.
    //
    // rg* diakses dari DUA thread (audio thread menulis di queueInput(), thread
    // pemanggil begin/finish membaca & mereset) tanpa lock eksplisit -- sama
    // seperti accumPeakL/R & peakHitThisWindow di atas, cukup untuk kebutuhan
    // pengukuran kasar ini (potensi meleset beberapa sample di titik mulai/selesai
    // bukan masalah untuk rata-rata RMS satu lagu penuh).
    @Volatile var measuringReplayGain: Boolean = false
        private set
    private var rgSumSquares: Double = 0.0
    private var rgSampleCount: Long = 0L
    private var rgPeak: Double = 0.0

    fun beginReplayGainMeasurement() {
        rgSumSquares = 0.0
        rgSampleCount = 0L
        rgPeak = 0.0
        measuringReplayGain = true
    }

    fun cancelReplayGainMeasurement() {
        measuringReplayGain = false
    }

    /**
     * Selesaikan pengukuran & kembalikan gain (dB) yang disarankan supaya RMS
     * rata-rata lagu ini mendekati target referensi [REPLAY_GAIN_TARGET_RMS_DB].
     * Null kalau tidak ada sample yang sempat terukur sama sekali (mis. lagu
     * langsung di-skip sebelum decode sempat jalan).
     *
     * CATATAN JUJUR: ini proksi RMS sederhana (mean-square seluruh sample yang
     * sempat lewat), BUKAN algoritma ReplayGain 2.0 / EBU R128 resmi (yang
     * butuh k-weighting/filter psychoacoustic + gating loudness penuh) --
     * cukup untuk menyamakan level KASAR antar lagu di satu koleksi, tapi
     * jangan diharapkan presisi setara software audio profesional. Gain juga dibatasi
     * supaya tidak mendorong PEAK asli lagu ini sampai clipping (headroom murni
     * dari perhitungan peak-nya sendiri, limiter di bawah tetap jadi jaring
     * pengaman kedua kalau EQ/Vocal menambah gain lagi setelahnya).
     */
    fun finishReplayGainMeasurement(): Double? {
        measuringReplayGain = false
        if (rgSampleCount <= 0L) return null
        val meanSquare = rgSumSquares / rgSampleCount
        if (meanSquare <= 0.0) return null
        val rmsDb = 10.0 * kotlin.math.log10(meanSquare)
        var gain = REPLAY_GAIN_TARGET_RMS_DB - rmsDb
        if (rgPeak > 0.0) {
            val peakDb = 20.0 * kotlin.math.log10(rgPeak)
            val maxGainBeforeClip = -peakDb
            if (gain > maxGainBeforeClip) gain = maxGainBeforeClip
        }
        return gain.coerceIn(-12.0, 12.0)
    }

    // ================================================================
    // BARU (patch "DSP control knobs"): Vocal enhancer (tombol VOCAL + knop
    // bass/treble khusus vokal), dan trio kontrol image stereo -- Balance,
    // Stereo Expansion, Mono/Stereo -- menyediakan sub-menu DSP tambahan.
    // ================================================================

    // --- VOCAL: dua filter peaking terpisah dari 10-band graphic EQ, jadi
    // tab EQU/NADA tidak ikut berubah saat knop vocal digeser. Ikut mati
    // bersama `bypass` (EQ BYPASS) karena tetap bagian dari tone-shaping,
    // sama seperti 10-band EQ.
    @Volatile var vocalEnabled: Boolean = false
        private set
    @Volatile var vocalBassDb: Double = 0.0
        private set
    @Volatile var vocalTrebleDb: Double = 0.0
        private set

    // --- STEREO IMAGE: balance, stereo expansion, dan mono/stereo SENGAJA
    // tidak ikut mati oleh EQ BYPASS -- di hardware amplifier klasik,
    // knop-knop ini berdiri sendiri di luar jalur tone control, jadi tetap
    // aktif walau EQ 10-band/Vocal sedang di-bypass.
    //   balance          : -1.0 (penuh ke kiri) .. 0.0 (tengah) .. 1.0 (penuh ke kanan)
    //   stereoExpansion  :  0.0 (mono penuh via M/S) .. 1.0 (normal) .. 2.0 (lebar maksimal)
    //   monoStereoOn     :  true -> paksa downmix L+R jadi mono di kedua channel,
    //                       mengalahkan stereoExpansion (mono selalu menang).
    @Volatile var balance: Double = 0.0
        private set
    @Volatile var stereoExpansion: Double = 1.0
        private set
    @Volatile var monoStereoOn: Boolean = false
        private set

    // BARU: dipanggil bersamaan dengan vuCallback (interval sample yang sama) supaya
    // Status Panel VFD bisa menampilkan status DSP Engine (aktif/bypass) dan indikator
    // PEAK (limiter/soft-clipper sedang menahan sinyal) secara real-time.
    //   dspActive  -> true selama EQ/Tape Saturation TIDAK di-bypass.
    //   peakActive -> true kalau ADA sample dalam window ini yang menyentuh/melewati
    //                 limiterThreshold (limiter benar-benar bekerja meredam gain).
    @Volatile var dspStateCallback: ((dspActive: Boolean, peakActive: Boolean) -> Unit)? = null
    private var peakHitThisWindow = false

    private var sampleRate = 44100
    private var channelCount = 2
    // FIX: sebelumnya onConfigure() selalu mengembalikan C.ENCODING_PCM_FLOAT apa pun
    // encoding input-nya. Akibatnya DefaultAudioSink SELALU mencoba membangun AudioTrack
    // dalam mode float untuk semua lagu (FLAC/WAV/AAC/MP3/Opus sama saja), walau
    // enableFloatOutput dari ExoPlayer sedang false / device tidak mendukungnya secara
    // langsung -> AudioTrack gagal dibuat -> ERROR_CODE_AUDIO_TRACK_INIT_FAILED untuK
    // SEMUA format. Sekarang encoding output EQ ini mengikuti encoding input, supaya
    // keputusan pakai float atau tidak tetap di tangan DefaultAudioSink/enableFloatOutput.
    private var outputEncoding = C.ENCODING_PCM_16BIT

    // Volatile reference untuk pertukaran filter yang aman antar thread
    @Volatile private var filters: Array<Array<BiquadFilter>> = arrayOf()
    private var currentPreset: EqPreset = flatTenBandPreset()
    @Volatile private var autoPreampLinear = 1.0

    private var accumPeakL = 0f
    private var accumPeakR = 0f
    private var samplesSinceVuEmit = 0
    private val vuEmitInterval = 512

    fun setPreset(preset: EqPreset) {
        currentPreset = preset
        rebuildFilters()
    }

    // ================================================================
    // SETTER PUBLIK -- VOCAL & STEREO IMAGE (dipanggil dari PlayerManager)
    // ================================================================

    fun setVocalEnabled(enabled: Boolean) {
        vocalEnabled = enabled
    }

    fun setVocalBass(db: Double) {
        vocalBassDb = db.coerceIn(-12.0, 12.0)
        rebuildVocalFilters()
    }

    fun setVocalTreble(db: Double) {
        vocalTrebleDb = db.coerceIn(-12.0, 12.0)
        rebuildVocalFilters()
    }

    fun setBalance(value: Double) {
        balance = value.coerceIn(-1.0, 1.0)
    }

    fun setStereoExpansion(value: Double) {
        stereoExpansion = value.coerceIn(0.0, 2.0)
    }

    fun setMonoStereo(monoOn: Boolean) {
        monoStereoOn = monoOn
    }

    // Frekuensi tetap untuk filter vocal -- BUKAN band ke-11/12 di preset,
    // sengaja terpisah total dari array `filters` (10-band graphic EQ) supaya
    // slider EQU/NADA tidak pernah ikut bergerak saat knop vocal digeser,
    // dan sebaliknya.
    //   VOCAL_BASS_HZ   : ~200Hz, mengisi kehangatan/body suara vokal tanpa
    //                     menyentuh sub-bass instrumen (beda tujuan dari
    //                     BASS di tab NADA yang di 31Hz).
    //   VOCAL_TREBLE_HZ : ~4.5kHz, area kejernihan/artikulasi vokal (presence),
    //                     di bawah area sibilan supaya tidak jadi "cempreng".
    private val VOCAL_BASS_HZ = 200.0
    private val VOCAL_TREBLE_HZ = 4500.0
    private val VOCAL_Q = 0.9

    // Array [channel][0 = bass, 1 = treble] -- volatile reference, sama pola
    // dengan `filters` di atas supaya pertukaran antar thread aman.
    @Volatile private var vocalFilters: Array<Array<BiquadFilter>> = arrayOf()

    @Synchronized
    private fun rebuildVocalFilters() {
        if (channelCount <= 0) return
        val newVocalFilters = Array(channelCount) { Array(2) { BiquadFilter() } }
        for (ch in 0 until channelCount) {
            newVocalFilters[ch][0].configure(
                BiquadType.PEAKING, sampleRate.toDouble(), VOCAL_BASS_HZ, vocalBassDb, VOCAL_Q
            )
            newVocalFilters[ch][1].configure(
                BiquadType.PEAKING, sampleRate.toDouble(), VOCAL_TREBLE_HZ, vocalTrebleDb, VOCAL_Q
            )
        }
        vocalFilters = newVocalFilters
    }

    @Synchronized
    private fun rebuildFilters() {
        if (channelCount <= 0) return
        val newFilters = Array(channelCount) { Array(currentPreset.bands.size) { BiquadFilter() } }
        for (ch in 0 until channelCount) {
            currentPreset.bands.forEachIndexed { i, band ->
                val type = when (band.type) {
                    0 -> BiquadType.LOW_SHELF
                    1 -> BiquadType.HIGH_SHELF
                    else -> BiquadType.PEAKING
                }
                val q = if (band.q <= 0.0) 0.707 else band.q
                newFilters[ch][i].configure(type, sampleRate.toDouble(), band.frequency, band.gain, q)
            }
        }

        // Auto-preamp / Headroom management.
        // CATATAN FIX (revisi ke-2): dua versi sebelumnya sama-sama SALAH di dua arah
        // berlawanan:
        //  - Versi lama: headroom dihitung dari gain band POSITIF TERBESAR saja -> terlalu
        //    konservatif, preset ke-boost malah kedengaran lemah/nggak "ngebass".
        //  - Versi sebelum ini: headroom dihapus total, cuma andalkan limiter -> untuk
        //    preset dengan band-band BERTETANGGA yang sama-sama boost (mis. gpt.json:
        //    +5.85dB di 31Hz DAN 62Hz sekaligus, Q lebar), respons gabungannya di frekuensi
        //    di antara keduanya bisa jauh MELEBIHI 5.85dB (band-band bertetangga saling
        //    menumpuk) -> limiter jadi bekerja terlalu berat/terus-menerus -> kedengaran
        //    pecah/distorsi ("gaber" versi lain).
        // Sekarang headroom dihitung dari respons magnitude GABUNGAN SEBENARNYA (bukan
        // tebakan) -- evaluasi kaskade seluruh band di banyak titik frekuensi 20Hz-20kHz,
        // cari puncak gain riil-nya, baru kompensasi persis sebesar itu. Preset yang tidak
        // punya band saling tumpuk tidak akan dipotong sama sekali (tetap terasa "ngebass"),
        // sementara preset yang band-nya saling menumpuk (seperti gpt.json) dapat headroom
        // yang pas -- tidak kurang (jadi pecah) atau berlebih (jadi lemah).
        var peakGain = 1.0
        if (newFilters.isNotEmpty()) {
            val cascade = newFilters[0]
            var f = 20.0
            while (f <= 20000.0) {
                var combined = 1.0
                for (bq in cascade) combined *= bq.magnitudeAt(f, sampleRate.toDouble())
                if (combined > peakGain) peakGain = combined
                f *= 1.03 // langkah ~1/24 oktaf, cukup rapat untuk menangkap puncak tumpukan band
            }
        }
        // FIX (patch kecil): headroomCompensation versi lama mengompensasi peakGain 100%,
        // seolah-olah lagu bisa punya sinyal full-scale PERSIS di frekuensi puncak tumpukan
        // band (mis. gpt.json: 31Hz+62Hz numpuk). Itu skenario terburuk yang nyaris tidak
        // pernah terjadi di materi musik nyata, tapi preamp tetap memotong SELURUH sinyal
        // (semua frekuensi, bukan cuma yang di puncak) sebesar itu terus-menerus -> hasilnya
        // preset EQ kedengaran jelas lebih pelan daripada flat, padahal secara warna suara
        // sudah lebih enak. Sekarang kompensasi dibagi dua bagian:
        //   1) headroomSafetyRatio (lihat deklarasi @Volatile var-nya di bawah, bisa
        //      diatur pengguna lewat slider "MAX LOUDNESS <-> SAFE HEADROOM" di tab
        //      BATAS) dari kelebihan gain (dalam dB) tetap dikompensasi di preamp,
        //      supaya program level rata-rata tidak kepotong terus oleh limiter.
        //   2) Sisanya SENGAJA dibiarkan lewat dan diserahkan ke limiter (envelope follower
        //      di queueInput()) di atas, yang transparan penuh di bawah threshold dan cuma
        //      menahan gain (bukan men-saturate bentuk gelombang) pada bagian yang benar-
        //      benar menyentuh headroom itu -- jadi cuma sample yang betulan mendekati
        //      worst-case yang kena redam, bukan seluruh track.
        // Hasilnya: preset ber-boost besar (seperti gpt.json) sekarang jauh lebih dekat
        // loudness-nya ke flat, tapi tetap tidak clipping keras karena limiter masih jaga
        // sisa headroom yang tidak dikompensasi di sini.
        // REVISI (patch "headroom slider"): baca dari field lokal SEKALI di sini
        // (bukan mengakses `headroomSafetyRatio` berkali-kali) -- kebiasaan yang
        // sama dengan `localFilters`/`localVocalFilters` di queueInput(), supaya
        // hasil perhitungan di bawah konsisten pakai satu nilai yang sama walau
        // ada thread lain yang kebetulan sedang mengubah headroomSafetyRatio lewat
        // setHeadroomSafetyRatio() di saat yang bersamaan.
        val safetyRatio = headroomSafetyRatio
        val peakGainDb = 20.0 * kotlin.math.log10(peakGain)
        val compensatedDb = peakGainDb * safetyRatio
        val headroomCompensation = 10.0.pow(-compensatedDb / 20.0)
        autoPreampLinear = (10.0.pow(currentPreset.preamp / 20.0) * headroomCompensation).coerceAtMost(1.0)

        // Penetapan atomic agar thread audio aman membaca array baru
        filters = newFilters
    }

    /**
     * GANTI DARI tanh() SOFT-CLIP KE CLEAN PEAK LIMITER:
     * tanh() adalah fungsi waveshaping -- dia MENAMBAHKAN harmonik/warna ke sinyal begitu
     * amplitudo melewati threshold, itu sifatnya "saturasi analog", bukan "transparan".
     * Limiter di bawah ini murni linear: envelope follower (attack cepat, release lebih
     * lambat, di-share L/R supaya image stereo tidak goyah) menentukan seberapa besar GAIN
     * (bukan bentuk gelombang) yang perlu diturunkan, lalu gain itu dikalikan rata ke
     * sample -- tidak ada fungsi non-linear yang disentuh ke sample itu sendiri. Di bawah
     * threshold, gain = 1.0 persis (byte-transparan), sama seperti sebelumnya.
     *
     * REVISI (patch "musical limiter tuning"): versi lama (attack 1ms, release 50ms flat,
     * TANPA lookahead, TANPA hold) punya dua masalah yang bikin transient besar (kick/snare)
     * kedengaran kaku/nge-duck dibanding limiter komersial:
     *
     *   1. ZERO-LATENCY ATTACK TANPA LOOKAHEAD -- envelope baru mulai naik SETELAH sample
     *      transient itu sendiri lewat, jadi puncak transient yang paling tajam bisa lolos
     *      sebelum gain reduction sempat "gigit", lalu kena hard-clip diam-diam di
     *      `coerceIn(-1f, 1f)` waktu ditulis ke output. Attack 1ms sebenarnya sudah termasuk
     *      cepat menurut rule of thumb (1-10ms), tapi TANPA lookahead, attack secepat apa pun
     *      tetap kebobolan oleh sample pertama si transient.
     *   2. RELEASE FLAT TANPA HOLD -- begitu level turun sedikit saja, envelope langsung mulai
     *      release (50ms, di ujung tercepat rentang wajar 50-200ms). Untuk transient beruntun
     *      yang berdekatan (kick lalu snare beberapa puluh ms kemudian), ini memicu siklus
     *      reduce -> recover -> reduce yang cepat -> kedengaran sebagai "pumping"/nge-duck.
     *
     * Solusinya BUKAN sekadar naik-turunkan angka attack/release, tapi menambah dua komponen
     * yang biasanya ada di limiter musikal/mastering (Ozone Maximizer, FabFilter L2, dst):
     *
     *   - LOOKAHEAD (delay line pendek, lihat limiterLookaheadSeconds & delayBuffers di bawah):
     *     audio ditunda beberapa ms, sementara envelope follower "melihat" peak dari sample
     *     yang BELUM ditunda -- jadi begitu delayed sample itu benar-benar sampai ke output,
     *     gain reduction sudah siap TEPAT WAKTU. Ini membuat attack bisa dilonggarkan (1ms ->
     *     3ms, delta gain per-sample lebih kecil -> distorsi intermodulasi lebih rendah) TANPA
     *     risiko overshoot/hard-clip seperti sebelumnya.
     *   - HOLD TIME (limiterHoldSeconds & holdCounter di bawah): envelope dikunci di level
     *     reduksi saat ini selama holdSamples, BARU setelah itu mulai release. Ini mencegah
     *     limiter "flutter" (reduce-recover-reduce cepat) di antara transient yang berdekatan.
     *
     * Trade-off yang perlu disadari:
     *   - Lookahead menambah latency TETAP sebesar limiterLookaheadSeconds (default 3ms) ke
     *     seluruh pipeline -- tidak masalah untuk playback musik biasa, tapi disebut di sini
     *     supaya jelas kalau nanti ada fitur lain yang butuh sample-accurate timing.
     *   - Release yang lebih lambat (50ms -> 120ms) lebih "musical"/natural dan mengurangi
     *     pumping, tapi untuk materi yang sangat loud & padat terus-menerus (mis. EDM
     *     full-compressed), gain reduction bisa "nyangkut" sedikit lebih lama dibanding
     *     release cepat -- attack lambat = lebih transparan tapi risiko sisa gain reduction
     *     lebih lama nempel; attack cepat = lebih "aman" langsung tapi lebih gampang pumping.
     *     120ms dipilih sebagai titik tengah yang condong ke musikal, bukan ke agresif.
     */
    private val limiterThresholdDb = -0.5
    private val limiterThreshold = 10.0.pow(limiterThresholdDb / 20.0) // ~0.9441 linear
    private val limiterAttackSeconds = 0.003    // REVISI: 1ms -> 3ms (aman berkat lookahead)
    private val limiterReleaseSeconds = 0.120   // REVISI: 50ms -> 120ms (kurangi pumping)
    private val limiterHoldSeconds = 0.010      // BARU: 10ms hold sebelum mulai release
    private val limiterLookaheadSeconds = 0.003 // BARU: 3ms lookahead, samakan dengan attack
    private var limiterAttackCoeff = 0.0
    private var limiterReleaseCoeff = 0.0
    private var limiterHoldSamples = 0
    // Envelope & hold counter di-share (stereo-linked) antar channel dalam satu frame --
    // lihat queueInput(). holdCounter di-reset tiap kali envelope naik (attack), dihitung
    // mundur tiap frame selama tidak ada attack baru; release cuma jalan kalau sudah 0.
    private var limiterEnvelope = 0.0
    private var limiterHoldCounter = 0

    // BARU (lookahead): delay line pendek per-channel. `scratch[ch]` (sample SETELAH EQ +
    // image stereo, SEBELUM limiter) ditulis ke sini, dan yang dibaca balik untuk PASS 2
    // adalah sample dari `limiterLookaheadSamples` frame yang lalu -- ring buffer sederhana,
    // baca-lalu-tulis di slot yang sama, nol alokasi di hot loop (cuma dialokasikan ulang
    // saat sampleRate/channelCount berubah lewat onConfigure()).
    private var limiterLookaheadSamples = 0
    @Volatile private var delayBuffers: Array<DoubleArray> = arrayOf()
    private var delayIndex = 0

    // Berapa persen dari headroom (dalam dB) yang dipotong lewat preamp; sisanya diserahkan
    // ke limiter. 1.0 = perilaku lama (paling aman, paling pelan). 0.0 = full loudness,
    // andalkan limiter sepenuhnya (paling nyaring, tapi limiter lebih sering "kerja").
    //
    // REVISI (patch "headroom slider"): sebelumnya nilai ini `private val` HARDCODE
    // 0.5 -- tidak ada slider/kontrol apa pun di UI, tidak dipersist, dan tidak
    // dilewatkan lewat SettingsRepository/PlayerManager sama sekali. Efeknya, untuk
    // preset EQ yang band-nya saling tumpuk (mis. boost besar berdekatan di beberapa
    // band bass, peakGain gabungan bisa +12dB lebih), autoPreampLinear di bawah
    // memotong ~6-7dB SEBELUM limiter sempat kerja sama sekali -- app ini jadi
    // kedengaran jauh lebih pelan daripada aplikasi pemutar musik lain untuk preset yang
    // nilai gain-nya identik, tanpa cara apa pun bagi pengguna untuk menyesuaikan
    // trade-off itu sendiri.
    //
    // Sekarang jadi `@Volatile var` (bisa diubah runtime lewat setHeadroomSafetyRatio
    // di bawah, dipersist di SettingsRepository, dan ada slider "MAX LOUDNESS <->
    // SAFE HEADROOM" di tab BATAS/Equalizer -- lihat EqualizerSubMenus.kt). Default
    // diturunkan dari 0.5 ke 0.3: titik tengah LAMA (0.5) ternyata masih terlalu
    // konservatif untuk selera kebanyakan orang yang membandingkan loudness app ini
    // dengan aplikasi lain; 0.3 membuat preset boost besar lebih dekat lagi ke loudness
    // flat, dengan limiter (envelope follower di queueInput()) tetap jadi jaring
    // pengaman kedua untuk sisa headroom yang sengaja tidak dikompensasi di sini.
    // Pengguna yang lebih mengutamakan keamanan/headroom murni daripada loudness
    // maksimal tetap bisa menggeser slider itu kembali ke nilai lebih tinggi
    // (sampai 1.0, perilaku paling konservatif) kapan pun.
    //
    // @Volatile di sini WAJIB (sama seperti bypass/limiterEnabled/replayGainEnabled
    // di atas): nilainya ditulis dari thread UI/ViewModel lewat setter di bawah, TAPI
    // dibaca di dalam rebuildFilters() yang juga bisa dipanggil dari alur lain
    // (onConfigure di audio thread) -- tanpa @Volatile, penulisan dari satu thread
    // tidak dijamin langsung terlihat oleh thread lain (stale/cached read), sumber
    // bug yang sangat sulit dilacak karena tidak selalu reproduksi.
    @Volatile var headroomSafetyRatio: Double = 0.3
        private set

    /**
     * Setter publik untuk [headroomSafetyRatio], dipanggil dari PlayerManager (yang
     * jadi jembatan tipis ke SettingsRepository/PlayerViewModel) -- pola sama persis
     * dengan setVocalBass/setStereoExpansion di atas.
     *
     * - coerce ke 0.0..1.0 (di luar rentang itu tidak masuk akal: 0.0 = paling
     *   nyaring/andalkan limiter penuh, 1.0 = paling konservatif/perilaku lama).
     * - langsung panggil rebuildFilters() ULANG dengan preset yang SEDANG aktif,
     *   supaya autoPreampLinear seketika dihitung ulang begitu slider digeser --
     *   BUKAN dibiarkan "menggantung" tanpa efek terdengar sampai pengguna
     *   kebetulan ganti preset lain (yang otomatis memanggil rebuildFilters() lewat
     *   setPreset()). Tanpa panggilan eksplisit ini, slider akan terasa seperti
     *   tidak berfungsi sampai preset berikutnya dipilih -- padahal nilainya sudah
     *   benar tersimpan, cuma belum "dipakai".
     *
     * Catatan: setter properti (`headroomSafetyRatio =`) sengaja dibuat `private`
     * di atas supaya Kotlin TIDAK men-generate `setHeadroomSafetyRatio(double)`
     * bawaan -- itu akan bentrok (platform declaration clash) dengan fungsi
     * eksplisit ini karena keduanya menghasilkan JVM signature yang sama persis.
     */
    fun setHeadroomSafetyRatio(value: Double) {
        headroomSafetyRatio = value.coerceIn(0.0, 1.0)
        rebuildFilters()
    }

    // REVISI (patch "musical limiter tuning"): selain koefisien attack/release, sekarang
    // juga menghitung holdSamples (dibulatkan dari limiterHoldSeconds) dan MENGALOKASIKAN
    // ULANG delay buffer lookahead -- ukurannya (limiterLookaheadSamples) bergantung sample
    // rate, jadi wajib dihitung ulang tiap kali sampleRate berubah (ganti lagu dengan sample
    // rate beda, mis. 44.1kHz -> 48kHz). Dipanggil dari onConfigure(), sama seperti sebelumnya.
    private fun recalculateLimiterCoefficients() {
        val sr = sampleRate.toDouble().coerceAtLeast(1.0)
        limiterAttackCoeff = exp(-1.0 / (limiterAttackSeconds * sr))
        limiterReleaseCoeff = exp(-1.0 / (limiterReleaseSeconds * sr))
        limiterHoldSamples = (limiterHoldSeconds * sr).toInt().coerceAtLeast(0)

        val newLookaheadSamples = (limiterLookaheadSeconds * sr).toInt().coerceAtLeast(1)
        val chCount = channelCount.coerceIn(1, frameScratch.size)
        limiterLookaheadSamples = newLookaheadSamples
        // Dialokasikan ulang dari nol (isi 0.0 = hening) -- sama seperti onFlush() untuk
        // filter EQ, ini WAJAR menyebabkan ~limiterLookaheadSeconds hening di awal playback
        // atau setelah seek, tidak terdengar (3ms) dan lebih aman daripada mempertahankan
        // isi buffer lama yang ukurannya sudah tidak cocok kalau sample rate berubah.
        delayBuffers = Array(chCount) { DoubleArray(newLookaheadSamples) }
        delayIndex = 0
    }

    // Scratch buffer untuk sample satu frame (semua channel) -- dialokasikan sekali saat
    // kelas dibuat, dipakai ulang tiap frame supaya hot loop di queueInput() nol-alokasi.
    private val frameScratch = DoubleArray(8)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        outputEncoding = inputAudioFormat.encoding
        rebuildFilters()
        rebuildVocalFilters()
        recalculateLimiterCoefficients()
        return AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, outputEncoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputEncoding = inputAudioFormat.encoding
        val frameCount = if (inputEncoding == C.ENCODING_PCM_FLOAT) {
            inputBuffer.remaining() / 4 / channelCount
        } else {
            inputBuffer.remaining() / 2 / channelCount
        }
        if (frameCount <= 0) return

        val outputBytesPerSample = if (outputEncoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val outputBuffer = replaceOutputBuffer(frameCount * outputBytesPerSample * channelCount)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val localFilters = filters // Cache referensi lokal agar thread-safe
        val activeChannels = channelCount.coerceAtMost(frameScratch.size)
        val scratch = frameScratch
        val threshold = limiterThreshold
        val attackC = limiterAttackCoeff
        val releaseC = limiterReleaseCoeff
        val holdSamples = limiterHoldSamples
        var envelope = limiterEnvelope
        var holdCounter = limiterHoldCounter
        // BARU (lookahead): cache referensi lokal, sama pola dengan localFilters di atas.
        val localDelayBuffers = delayBuffers
        val lookaheadSize = limiterLookaheadSamples
        var delayIdx = delayIndex

        for (frame in 0 until frameCount) {

            // ============================================================
            // PASS 1a: baca + 10-band EQ + Vocal enhancer (kalau aktif)
            // tiap channel. Belum dihitung PEAK-nya dulu -- image stereo
            // (mono/expansion/balance) di PASS 1b bisa mengubah amplitudo,
            // jadi PEAK yang dipakai limiter harus dihitung SETELAH itu.
            // ============================================================
            val localVocalFilters = vocalFilters // cache referensi lokal, thread-safe
            for (ch in 0 until activeChannels) {
                var sample: Double = if (inputEncoding == C.ENCODING_PCM_FLOAT) {
                    inputBuffer.float.toDouble()
                } else {
                    (inputBuffer.short / 32768.0)
                }

                // BARU (v1.9): ukur RMS dari sample MENTAH (sebelum EQ/gain apa pun)
                // kalau sedang dalam mode pengukuran REPLAY GAIN -- lihat
                // beginReplayGainMeasurement/finishReplayGainMeasurement di atas.
                if (measuringReplayGain) {
                    rgSumSquares += sample * sample
                    rgSampleCount++
                    val mag = abs(sample)
                    if (mag > rgPeak) rgPeak = mag
                }

                // BARU (v1.9): terapkan gain REPLAY GAIN (kalau ada & aktif) SEBELUM
                // EQ/Vocal -- lihat komentar panjang di deklarasi replayGainEnabled
                // untuk kenapa ini di luar gerbang `bypass` tapi tetap wajib mati saat
                // bitPerfectMode.
                if (replayGainEnabled && !bitPerfectMode) {
                    sample *= replayGainLinear
                }

                if (!bitPerfectMode && !bypass && localFilters.size > ch) {
                    val chFilters = localFilters[ch]
                    for (band in chFilters) {
                        sample = band.process(sample)
                    }
                    sample *= autoPreampLinear

                    if (vocalEnabled && localVocalFilters.size > ch) {
                        sample = localVocalFilters[ch][0].process(sample)
                        sample = localVocalFilters[ch][1].process(sample)
                    }
                }

                scratch[ch] = sample
            }

            // ============================================================
            // PASS 1b: IMAGE STEREO -- Mono/Stereo, Stereo Expansion, lalu
            // Balance. Sengaja TIDAK ikut digerbang oleh `bypass` (EQ
            // BYPASS) -- di amplifier klasik, knop-knop ini berdiri sendiri
            // di luar jalur tone control, jadi tetap berfungsi walau EQ
            // sedang dimatikan sementara. Hanya berlaku untuk sumber >= 2
            // channel; sumber mono asli dibiarkan apa adanya.
            // ============================================================
            if (activeChannels >= 2 && !bitPerfectMode) {
                var l = scratch[0]
                var r = scratch[1]

                if (monoStereoOn) {
                    // Mono selalu menang atas Stereo Expansion.
                    val mid = (l + r) * 0.5
                    l = mid
                    r = mid
                } else if (stereoExpansion != 1.0) {
                    // Mid/Side widening: 0.0 = mono penuh (side dibuang),
                    // 1.0 = citra stereo asli, 2.0 = lebar maksimal (side x2).
                    val mid = (l + r) * 0.5
                    val side = (l - r) * 0.5 * stereoExpansion
                    l = mid + side
                    r = mid - side
                }

                if (balance != 0.0) {
                    // balance > 0 -> geser ke kanan (channel kiri diredam).
                    // balance < 0 -> geser ke kiri (channel kanan diredam).
                    if (balance > 0.0) l *= (1.0 - balance) else r *= (1.0 + balance)
                }

                scratch[0] = l
                scratch[1] = r
            }

            // ============================================================
            // Cari PEAK gabungan L/R di frame ini (stereo-linked) SETELAH
            // image stereo di atas, supaya limiter menahan SEMUA channel
            // dengan gain yang sama berdasarkan sinyal yang benar-benar
            // akan ditulis ke output.
            // ============================================================
            var framePeak = 0.0
            for (ch in 0 until activeChannels) {
                val mag = abs(scratch[ch])
                if (mag > framePeak) framePeak = mag
            }

            // ============================================================
            // ENVELOPE FOLLOWER (stereo-linked, satu envelope untuk semua
            // channel): attack (3ms) saat sinyal naik, HOLD (10ms) begitu
            // sinyal mulai turun sebelum akhirnya release (120ms) -- lihat
            // catatan panjang REVISI "musical limiter tuning" di deklarasi
            // limiterAttackSeconds/limiterHoldSeconds/limiterReleaseSeconds
            // untuk alasan kenapa hold ditambahkan (mencegah pumping/flutter
            // di antara transient beruntun seperti kick+snare berdekatan).
            // Tetap murni linear, TANPA menyentuh bentuk gelombang sample.
            //
            //   - framePeak > envelope  -> ATTACK: envelope naik cepat,
            //     DAN reset holdCounter (transient baru mulai, tunda dulu
            //     kapan pun release nanti boleh mulai).
            //   - holdCounter > 0       -> HOLD: envelope DIKUNCI (coeff=1.0
            //     artinya rumus di bawah menghasilkan envelope tetap sama
            //     persis), holdCounter dihitung mundur.
            //   - selain itu            -> RELEASE: envelope turun pelan.
            // ============================================================
            var frameGain = 1.0
            if (limiterEnabled && !bitPerfectMode) {
                val coeff: Double
                if (framePeak > envelope) {
                    coeff = attackC
                    holdCounter = holdSamples
                } else if (holdCounter > 0) {
                    coeff = 1.0 // HOLD: envelope tidak berubah selama holdCounter masih berjalan
                    holdCounter--
                } else {
                    coeff = releaseC
                }
                envelope = coeff * envelope + (1.0 - coeff) * framePeak
                if (envelope > threshold) {
                    peakHitThisWindow = true
                    frameGain = threshold / envelope
                }
            }

            // ============================================================
            // PASS 2 (lookahead): tulis scratch[ch] (sample SETELAH EQ +
            // image stereo, SEBELUM limiter) ke delay buffer, baca balik
            // sample dari `lookaheadSize` frame yang lalu, BARU kalikan
            // dengan frameGain yang dihitung dari framePeak sinyal yang
            // MASIH BELUM ditunda -- inilah intinya lookahead: envelope
            // sudah "menyiapkan" gain reduction sebelum sample yang
            // butuh diredam itu benar-benar sampai ke output, jadi attack
            // 3ms di atas aman dari overshoot/hard-clip walau lebih
            // lambat dari versi lama (1ms, zero-latency, gampang kebobolan).
            // ============================================================
            for (ch in 0 until activeChannels) {
                val delayed: Double
                if (limiterEnabled && !bitPerfectMode && localDelayBuffers.size > ch &&
                    localDelayBuffers[ch].size == lookaheadSize
                ) {
                    val buf = localDelayBuffers[ch]
                    delayed = buf[delayIdx]
                    buf[delayIdx] = scratch[ch]
                } else {
                    // Limiter mati / bit-perfect / buffer belum siap -> jangan tunda sama
                    // sekali, langsung teruskan sample apa adanya (frameGain sudah 1.0 di
                    // jalur ini juga, lihat blok di atas).
                    delayed = scratch[ch]
                }
                val outSample = delayed * frameGain
                val f = outSample.toFloat().coerceIn(-1f, 1f)
                if (outputEncoding == C.ENCODING_PCM_FLOAT) {
                    outputBuffer.putFloat(f)
                } else {
                    val s16 = (f * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                    outputBuffer.putShort(s16)
                }

                val mag = abs(f)
                if (activeChannels == 1) {
                    if (mag > accumPeakL) accumPeakL = mag
                    if (mag > accumPeakR) accumPeakR = mag
                } else if (ch == 0) {
                    if (mag > accumPeakL) accumPeakL = mag
                } else if (ch == 1) {
                    if (mag > accumPeakR) accumPeakR = mag
                }
            }

            // BARU (lookahead): majukan ring buffer SATU KALI per frame (bukan per-channel
            // -- semua channel di frame yang sama berbagi slot indeks yang sama, supaya
            // L/R tetap delay yang identik/stereo-linked), wrap-around pakai modulo.
            if (lookaheadSize > 0) {
                delayIdx++
                if (delayIdx >= lookaheadSize) delayIdx = 0
            }
        }

        limiterEnvelope = envelope
        limiterHoldCounter = holdCounter
        delayIndex = delayIdx

        samplesSinceVuEmit += frameCount
        if (samplesSinceVuEmit >= vuEmitInterval) {
            vuCallback?.invoke(accumPeakL, accumPeakR)
            dspStateCallback?.invoke(!bitPerfectMode && !bypass, peakHitThisWindow)
            accumPeakL = 0f
            accumPeakR = 0f
            peakHitThisWindow = false
            samplesSinceVuEmit = 0
        }

        outputBuffer.flip()
    }

    override fun onFlush() {
        filters.forEach { chFilters -> chFilters.forEach { it.reset() } }
        vocalFilters.forEach { chFilters -> chFilters.forEach { it.reset() } }
        limiterEnvelope = 0.0
        // BARU (lookahead/hold): reset holdCounter & kosongkan delay buffer supaya tidak
        // ada sisa audio dari lagu/seek sebelumnya yang "bocor" keluar lookaheadSamples
        // frame kemudian -- sama alasannya dengan filters.forEach { it.reset() } di atas,
        // cuma di sini deep-clear array dengan fill(0.0) alih-alih objek reset().
        limiterHoldCounter = 0
        delayBuffers.forEach { it.fill(0.0) }
        delayIndex = 0
        accumPeakL = 0f
        accumPeakR = 0f
        peakHitThisWindow = false
        samplesSinceVuEmit = 0
    }

    override fun onReset() {
        filters = arrayOf()
        vocalFilters = arrayOf()
        limiterEnvelope = 0.0
        limiterHoldCounter = 0
        delayBuffers = arrayOf()
        delayIndex = 0
        accumPeakL = 0f
        accumPeakR = 0f
        peakHitThisWindow = false
        samplesSinceVuEmit = 0
    }
}
