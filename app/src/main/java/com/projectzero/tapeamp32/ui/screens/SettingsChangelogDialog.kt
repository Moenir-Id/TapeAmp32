package com.projectzero.tapeamp32.ui.screens

import android.provider.Settings
import com.projectzero.tapeamp32.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.*

// Changelog entry data + the dialog that renders the app's changelog list.
// Split out of SettingsScreen.kt.

internal data class ChangelogEntry(
    val version: String,
    val tagline: String,
    val notes: List<String>
)

internal val changelogEntries = listOf(
    // FIX AKAR MASALAH (v2.6): entri PALING ATAS (rilis aktif saat ini) sekarang
    // pakai "v" + BuildConfig.VERSION_NAME, bukan literal "v2.x" yang diketik
    // manual -- supaya tag versi di sini tidak mungkin lagi ketinggalan dari
    // versionName di app/build.gradle.kts. Entri-entri LAMA di bawahnya tetap
    // literal string apa adanya karena itu catatan sejarah versi yang sudah rilis
    // dan tidak boleh ikut berubah otomatis.
    ChangelogEntry(
        version = "v${BuildConfig.VERSION_NAME}",
        tagline = "Fix tap/geser baris lirik yang tidak memindahkan posisi playback lagu.",
        notes = listOf(
            "Fix: tap baris lirik tidak melakukan seek -- sebelumnya halaman Lirik cuma menampilkan baris yang sedang berjalan tanpa handler tap sama sekali, jadi menyentuh atau menggeser ke baris lirik tertentu tidak memindahkan posisi playback lagu. Sekarang tap pada baris lirik memanggil seekTo sehingga playback langsung lompat ke waktu baris tersebut."
        )
    ),
    ChangelogEntry(
        version = "v2.5",
        tagline = "Fix bitrate stream yang selalu tampil 0 kbps di halaman Streaming.",
        notes = listOf(
            "Fix: bitrate di panel Stream Information selalu 0 kbps -- akar masalahnya, field bitrateKbps di setiap stasiun cuma di-hardcode ke 0 saat ditambahkan lewat \"ADD STREAM URL\" dan tidak pernah diisi dari mana pun, jadi SEMUA stasiun (tidak ada stasiun preset bawaan, semuanya custom) selalu menampilkan angka statis itu. Sekarang bitrate dibaca LIVE dari stream yang sedang didecode: diutamakan header icy-br yang dikirim server radio Shoutcast/Icecast (paling akurat), fallback ke bitrate container kalau server tidak kirim ICY sama sekali.",
            "Kalau bitrate stream sungguhan tidak diketahui (server tidak kirim info apa pun), panel Stream Information & badge kecil di daftar Favorit sekarang menampilkan \"--\" alih-alih \"0 kbps\" yang menyesatkan seolah stream rusak."
        )
    ),
    ChangelogEntry(
        version = "v2.4",
        tagline = "Cassette Side A/B -- double tap bodi kaset untuk pindah Library <-> Stream, label sisi kaset ikut sumber audio yang aktif.",
        notes = listOf(
            "Baru: double tap di bodi kaset (bukan di roda/tape window, supaya tidak bentrok dengan gesture rotary seeking) untuk pindah antara Side A (Library) dan Side B (Stream). Label \"A\"/\"B\" di kaset otomatis ikut sumber audio yang benar-benar aktif -- diputar lewat double tap, dipilih dari layar Streaming, next/prev di tape deck, ataupun (nanti) dipicu otomatis dari Settings, semuanya bikin label ikut pindah sendiri tanpa jalur terpisah-pisah.",
            "Pindah ke Side B otomatis melanjutkan stream terakhir yang pernah diputar (URL + nama stasiun disimpan persisten, bertahan walau app ditutup total). Kalau belum pernah streaming sama sekali, double tap langsung membuka layar Streaming supaya bisa pilih stasiun dulu.",
            "Pindah balik ke Side A melanjutkan lagu library yang sedang diputar sebelum pindah ke stream, di posisi terakhirnya -- bukan cuma balik label tanpa audio.",
            "Label huruf \"A\"/\"B\" & content description untuk pembaca layar (\"Side A – Library\" / \"Side B – Stream\") sekarang string resource penuh, ikut sistem lokal ~10 bahasa yang sudah ada -- bukan literal hardcoded.",
            "Catatan desain: label Side A/B sengaja tidak disimpan sebagai state manual terpisah, melainkan diturunkan langsung dari status streaming yang sedang aktif -- jadi fitur otomatis apa pun di masa depan yang memulai stream dari Settings (mis. auto-resume saat startup) akan otomatis membuat kaset pindah ke Side B juga, tanpa perlu ubah kode di tempat lain.",
            "Fix: label \"App Version\" di Settings > System > About sempat ketinggalan di \"2.2\" walau app sudah naik ke versionName \"2.3\" sebelumnya -- sekarang disamakan lagi manual ke \"2.4\"."
        )
    ),
    ChangelogEntry(
        version = "v2.3",
        tagline = "Widget Home Screen -- kontrol player (prev/play-pause/next + judul/artis lagu) langsung dari home screen, tanpa buka app.",
        notes = listOf(
            "Baru: widget home screen. Tambahkan lewat cara biasa (tekan lama home screen kosong > Widgets > TapeAmp 32).",
            "Tombol play/pause/next/prev di widget memicu broadcast ACTION_MEDIA_BUTTON standar Android, ditangkap oleh MediaButtonReceiver yang sudah dipakai kontrol statusbar/lockscreen -- jalur yang sama persis, bukan logic baru. Ketuk area judul/artis untuk buka app langsung.",
            "Tampilan judul/artis & ikon play/pause di widget di-refresh setiap kali status playback/lagu berubah (bukan menunggu siklus refresh bawaan Android yang minimal 30 menit sekali) -- lewat listener baru di PlaybackService, selama app/service masih hidup di background.",
            "Sengaja pakai RemoteViews klasik (bukan Glance) supaya tidak menambah dependency baru untuk fitur bergini kecil -- konsekuensinya tampilan widget statis (warna preset \"Gold Retro\"), belum ikut tema aktif yang dipilih di dalam app.",
            "Jujur diakui: kalau PlaybackService dan proses app sama sekali tidak hidup (mis. di-force-stop manual dari Settings Android atau tidak pernah dibuka sejak install), widget akan menampilkan status terakhir yang sempat tersimpan sampai app dibuka minimal sekali lagi -- ini batasan bawaan RemoteViews, bukan yang bisa dihindari sepenuhnya tanpa proses background permanen.",
            "Tombol widget cuma memicu broadcast ACTION_MEDIA_BUTTON standar, sama sekali tidak menyentuh jalur audio -- jadi status BIT-PERFECT MODE (aktif/nonaktif) tidak terpengaruh apa pun cara lagu dikontrol, lewat widget maupun dari dalam app."
        )
    ),
    ChangelogEntry(
        version = "v2.2",
        tagline = "Crossfade beneran di tab BATAS -- key-nya (FADE_SECONDS) sudah ada sejak lama tapi yatim, tidak pernah tersambung ke DSP. Plus verifikasi kontrol tombol headset/Bluetooth.",
        notes = listOf(
            "Baru: toggle \"CROSSFADE\" di tab BATAS (Equalizer), plus pilihan durasi overlap 2/4/6/8 detik. Beda dari GAPLESS (cuma menghilangkan jeda hening, tanpa tumpang tindih): lagu sekarang benar-benar meluruh (fade-out) SAMBIL lagu berikutnya masuk (fade-in) berbarengan, lewat jalur playback kedua yang berdiri sendiri di sisi Android -- bukan simulasi dengan jeda dipercepat.",
            "Hanya berlaku saat lagu berpindah OTOMATIS di akhir lagu (auto-advance dalam antrian, termasuk Repeat Semua/Satu Lagu) -- next/prev manual tetap instan seperti biasa, supaya tombol tetap terasa responsif dan tidak \"menunggu\" fade selesai.",
            "Kontrol play/pause/next/prev lewat tombol headset kabel maupun Bluetooth (AVRCP) diverifikasi ulang -- sudah berfungsi lewat MediaSession + antrian penuh yang dibangun sejak fix controlbar statusbar/lockscreen, jadi tidak ada perubahan kode baru untuk ini; ditandai di sini supaya statusnya tercatat jelas, bukan diasumsikan diam-diam.",
            "Jujur diakui: selama jendela overlap crossfade berjalan (1-8 detik di sekitar pergantian lagu), audio lagu berikutnya dikirim lewat jalur playback kedua yang TIDAK melewati pipeline EQ/Vocal/Stereo/Limiter/BIT-PERFECT MODE -- baru ikut EQ/DSP lagi begitu jalur utama sendiri yang mengambil alih lagu itu secara alami di titik pergantian. Untuk jendela sesingkat ini dampaknya kecil, tapi CROSSFADE dan BIT-PERFECT MODE tetap dua hal yang berbeda tujuan: BIT-PERFECT MODE menjamin sample tidak diubah SAMA SEKALI selama lagu diputar penuh, dan itu tetap berlaku 100% di luar jendela overlap singkat ini."
        )
    ),
    ChangelogEntry(
        version = "v2.1",
        tagline = "Sleep Timer beneran -- key & flow-nya sudah ada sejak lama (SLEEP_TIMER_MIN) tapi tidak pernah dipakai siapa pun, sekarang diimplementasikan penuh.",
        notes = listOf(
            "Baru: bagian \"Sleep Timer\" di Settings > System, dengan dua opsi independen (memilih salah satu otomatis membatalkan yang lain). Pertama, slider durasi tetap 0-90 menit -- geser ke 0 untuk mematikan.",
            "Kedua, toggle \"Stop After Current Track\" -- berhenti begitu lagu yang SEDANG diputar sekarang selesai, tanpa hitung mundur waktu sama sekali. Berfungsi juga selagi Repeat Semua/Satu Lagu aktif: repeatMode ExoPlayer dipaksa OFF sementara khusus untuk lagu ini supaya benar-benar berhenti, lalu dikembalikan otomatis ke pilihan Repeat pengguna begitu timer ini selesai bertugas -- pilihan Repeat pengguna sendiri TIDAK ikut berubah/reset.",
            "Status hidup (sisa waktu mm:ss, atau \"menunggu lagu selesai\") tampil di dua tempat: kartu status di Settings, dan baris baru \"SLEEP\" di Status Panel VFD (di bawah OFFLOAD, di atas PEAK) di layar Pemutar -- jadi terlihat langsung tanpa perlu buka Settings.",
            "Tombol \"Cancel Sleep Timer\" muncul di Settings begitu salah satu timer aktif, untuk membatalkan kapan saja sebelum waktunya habis."
        )
    ),
    ChangelogEntry(
        version = "v2.0",
        tagline = "Backup & Restore SEMUA pengaturan sekaligus, bukan cuma preset EQ -- plus fix App Version yang salah & changelog v1.9 yang sempat ketinggalan.",
        notes = listOf(
            "Baru: \"Backup All Settings\" & \"Restore All Settings\" di menu Settings baru: Backup & Restore. Backup ditulis ke satu file .json lewat SAF \"Save As\" (pola sama seperti EXPORT preset EQ di tab BATAS sejak v1.2), tapi sekarang mencakup SELURUH pengaturan sekaligus: semua toggle/dropdown, preset EQ custom, playlist, peta EQ per-lagu, peta REPLAY GAIN per-lagu, posisi knop Vocal/Stereo, Theme Accent Color, dan seterusnya -- bukan cuma satu preset EQ aktif seperti EXPORT lama.",
            "Restore membaca file backup itu lewat SAF \"Open\", dengan dialog konfirmasi dulu karena aksi ini mengganti SELURUH pengaturan tersimpan saat ini dan tidak bisa dibatalkan.",
            "Jujur diakui: karena sebagian besar pengaturan hanya dipulihkan ke layar SEKALI saat app baru dibuka (bukan dipantau live selama app berjalan), hasil Restore baru terlihat PENUH di seluruh app setelah app ditutup total & dibuka ulang -- app mengingatkan ini lewat pesan begitu Restore selesai.",
            "Fix: label \"App Version\" di Settings > System > About sebelumnya hardcode salah ke \"1.5\" sejak v1.5 dan tidak pernah diperbarui lagi walau app sudah beberapa kali naik versi -- sekarang disamakan manual dengan versionName build ini.",
            "Fix: entri changelog v1.9 (REPLAY GAIN) sebelumnya cuma sempat ditambahkan ke TapeAmp32_changelog.html, tidak pernah ditambahkan ke Changelog di dalam app (Settings > System > View Changelog) -- sekarang keduanya identik, lihat entri v1.9 di bawah."
        )
    ),
    ChangelogEntry(
        version = "v1.9",
        tagline = "REPLAY GAIN beneran -- fitur yang sama namanya sudah pernah dihapus di v1.4.1 karena ternyata palsu, sekarang diimplementasikan ulang secara jujur.",
        notes = listOf(
            "Baru: toggle \"REPLAY GAIN\" di tab BATAS (Equalizer). Saat aktif, level (loudness) tiap lagu diukur dari sample PCM ASLI yang mengalir lewat pipeline audio bit-perfect (RMS, sebelum EQ/Vocal disentuh) begitu lagu itu diputar untuk pertama kali, lalu gain hasil pengukurannya \"diingat\" dan diterapkan otomatis setiap kali lagu yang sama diputar ulang -- supaya level antar lagu di koleksi lebih rata, tidak ada lagi lagu yang tiba-tiba jauh lebih pelan/nyaring dari lagu sebelumnya.",
            "Status kecil di bawah toggle menampilkan gain (dB) tersimpan untuk lagu yang sedang diputar, atau status \"belum terukur\" untuk lagu yang baru pertama kali dijalankan, lengkap tombol HAPUS untuk mengukur ulang lagu tersebut dari awal kapan saja.",
            "Gain dibatasi otomatis supaya tidak mendorong puncak sinyal asli lagu sampai clipping, dan REPLAY GAIN otomatis nonaktif selagi BIT-PERFECT MODE menyala -- bit-perfect artinya sample tidak boleh diubah sama sekali, termasuk oleh gain normalisasi sekalipun.",
            "Jujur diakui: ini proksi RMS sederhana, bukan algoritma ReplayGain 2.0 / EBU R128 resmi (yang butuh k-weighting & loudness gating penuh) -- cukup untuk menyamakan level kasar antar lagu di satu koleksi, jangan diharapkan presisi setara software audio profesional. Pengukuran juga jalan per-lagu saat diputar (bukan analisis batch seluruh library di background), jadi lagu yang belum pernah diputar sama sekali belum punya gain tersimpan."
        )
    ),
    ChangelogEntry(
        version = "v1.8.1",
        tagline = "Fix: submenu BATAS sekarang bisa digulir -- akar masalah toggle 'kelihatan gak seragam' di v1.8 ternyata bukan soal ukuran, tapi ke-clip.",
        notes = listOf(
            "Fix akar masalah: submenu BATAS (SOFT LIMITER/EQ BYPASS/BIT-PERFECT MODE/AUTO EQ PER LAGU) sebelumnya TIDAK bisa digulir, sedangkan panel EQ di sebelahnya tinggi tetap (fillMaxHeight). Di layar/jendela yang lebih pendek -- window desktop kecil, split-screen, atau HP dengan status/nav bar besar -- 4 kartu toggle tidak selalu muat, dan kartu PALING BAWAH (AUTO EQ PER LAGU) ke-clip oleh batas panel: deskripsinya hilang dan rocker switch-nya kepotong di tepi bawah. Ini yang bikin toggle itu TERLIHAT beda ukuran di v1.8, padahal komponennya (EqToggleRow/EqRockerSwitch) sudah identik untuk keempatnya sejak awal.",
            "Submenu BATAS sekarang bisa digulir penuh, jadi keempat kartu toggle -- termasuk status ekstra di dalamnya (offload di BIT-PERFECT MODE, preset tersimpan di AUTO EQ PER LAGU) -- selalu terlihat utuh apa pun tinggi layarnya, bukan cuma di layar yang cukup tinggi."
        )
    ),
    ChangelogEntry(
        version = "v1.8",
        tagline = "Ukuran kartu toggle di tab BATAS diseragamkan, dan status OFFLOAD hardware akhirnya tampil real-time di UI.",
        notes = listOf(
            "Fix: kartu toggle \"AUTO EQ PER LAGU\" sebelumnya terlihat beda ukuran/proporsi dari SOFT LIMITER, EQ BYPASS, dan BIT-PERFECT MODE -- status preset tersimpan di bawahnya dulu cuma teks lepas berinset 2dp tanpa kartu sendiri. Sekarang status itu (dan status OFFLOAD baru di bawah, lihat poin berikut) dirender DI DALAM kartu toggle yang sama, dengan inset 12dp yang identik dengan toggle lain -- seluruh kartu di tab BATAS sekarang konsisten satu bahasa visual, tinggi kartu cuma beda kalau memang ada status ekstra untuk ditampilkan.",
            "Baru: status OFFLOAD hardware AKTUAL sekarang tampil real-time di bawah toggle \"BIT-PERFECT MODE\" begitu toggle-nya aktif -- menunjukkan apakah lagu yang sedang diputar benar-benar lewat jalur direct-path ke DAC, atau otomatis fallback ke bypass software (tetap 100% bit-perfect, cuma tanpa hardware offload tambahan).",
            "Baru: baris \"OFFLOAD\" ditambahkan di Status Panel VFD (di bawah HI-RES, di atas PEAK), jadi status ini juga terlihat langsung dari layar Pemutar tanpa perlu buka tab Equalizer.",
            "Ini menjawab catatan \"JUJUR diakui\" di changelog v1.6 -- status offload AKTUAL per lagu sebelumnya cuma bisa dikonfirmasi manual lewat adb logcat saat testing di device fisik, sekarang sudah muncul langsung di UI."
        )
    ),
    ChangelogEntry(
        version = "v1.7",
        tagline = "EQ Per-Lagu Otomatis -- preset EQ diingat per lagu dan dipasang sendiri saat lagu diputar ulang.",
        notes = listOf(
            "Baru: toggle \"AUTO EQ PER LAGU\" di tab BATAS. Saat aktif, preset EQ yang dipilih dari dropdown otomatis \"diingat\" untuk lagu yang sedang diputar, lalu dipasang sendiri lagi begitu lagu itu diputar ulang -- next/prev, lockscreen, shuffle, maupun replay -- tanpa perlu ganti preset manual tiap pindah lagu.",
            "Status kecil muncul di bawah toggle menampilkan preset yang tersimpan untuk lagu yang sedang diputar, lengkap tombol HAPUS untuk melupakan preset lagu tersebut kapan saja.",
            "Lagu yang belum pernah diberi preset (selagu toggle aktif) tidak diganggu -- preset yang sedang aktif tetap dipakai apa adanya.",
            "Mematikan toggle tidak menghapus data yang sudah tersimpan -- cuma berhenti auto-ganti, tinggal nyalakan lagi kapan pun untuk memakainya lagi."
        )
    ),
    ChangelogEntry(
        version = "v1.6",
        tagline = "Knop & slider EQ proporsional ke lebar layar, dan toggle baru BIT-PERFECT MODE (software + hardware offload) di tab BATAS.",
        notes = listOf(
            "Knop (EqKnob) dan slider (EqSlider) di layar Equalizer sekarang ikut menyesuaikan lebar layar perangkat -- sebelumnya ukurannya angka dp tetap yang sama persis di HP kecil maupun HP/tablet lebar.",
            "Baru: toggle \"BIT-PERFECT MODE\" di tab BATAS. Beda dari EQ BYPASS yang cuma mematikan EQ 10-band/Vocal, toggle ini sekaligus melewati Balance, Stereo Expansion, Mono/Stereo, dan Limiter di sisi software (sample diteruskan apa adanya, kode kita tidak menyentuhnya sama sekali) -- generik untuk semua kombinasi device/DAC, bukan hardcode merk tertentu.",
            "Toggle yang sama JUGA meminta jalur AUDIO OFFLOAD hardware ke ExoPlayer (direct-path ke chip DSP device/USB DAC, bypass software sepenuhnya) kalau device & format lagu yang sedang diputar mendukungnya. Kalau tidak didukung, otomatis fallback diam-diam ke bypass software di atas -- tidak ada silent failure, lagu tetap jalan.",
            "Fix konflik potensial dengan fitur FF/RW kaset (scrub speed/pitch): offload sekarang eksplisit minta dukungan speed-change juga, supaya ExoPlayer tidak memilih jalur offload untuk device yang tidak sanggup menjaga fitur scrub tetap berfungsi.",
            "Posisi knop EQ/Vocal/Stereo/Limiter lain TIDAK ikut berubah/reset saat BIT-PERFECT MODE aktif -- cuma dibekukan sementara, kembali persis ke posisi terakhir begitu dimatikan.",
            "JUJUR diakui: status offload AKTUAL (benar-benar aktif atau fallback) per lagu belum ditampilkan di UI/Status Panel VFD versi ini -- baru bisa dikonfirmasi manual lewat adb logcat saat testing di device fisik."
        )
    ),
    ChangelogEntry(
        version = "v1.5.1",
        tagline = "Fix: gesture seek roda kaset sekarang benar-benar nyambung, tidak lagi terputus-putus.",
        notes = listOf(
            "Fix: memutar roda kaset untuk seek manual sebelumnya terasa \"patah-patah\" -- gesture drag terus-menerus di-restart dari nol setiap kali layar Pemutar recompose (bisa beberapa kali per detik saat lagu berjalan), karena gesture di-key oleh callback yang selalu jadi objek baru tiap recomposition.",
            "Efek sampingnya, efek glitch (garis distorsi cyan/magenta) yang seharusnya mengikuti gerakan jari malah muncul-hilang acak. Sekarang callback tidak lagi memicu restart gesture, jadi seek & efek glitch mengikuti gerakan jari dengan mulus."
        )
    ),
    ChangelogEntry(
        version = "v1.5",
        tagline = "Fix Next setelah pencarian, inset SCREEN toggle, knop EQ lanjutan lebih besar, dan dukungan Split Screen.",
        notes = listOf(
            "Fix: tombol NEXT sekarang berfungsi normal setelah memutar lagu dari hasil pencarian di Library -- sebelumnya antrian pemutaran ikut dipersempit jadi hanya hasil pencarian, sehingga NEXT terasa \"macet\" di lagu yang sama.",
            "Fix: saat toggle SCREEN (immersive) dimatikan, sekarang HANYA status bar & navigation bar yang menyempitkan konten -- sebelumnya sisi kiri & kanan ikut menyempit bersamaan dengan bagian atas.",
            "Knop di tab lanjutan VOCAL & STEREO pada Equalizer diperbesar sedikit (76dp -> 84dp) supaya lebih nyaman diputar dengan jari.",
            "Baru: dukungan Split Screen/Multi-Window -- NavRail, sidebar Library, panel kontrol Player, dan VU Meter otomatis beralih ke tata letak ringkas (ikon saja, lebih ramping) saat jendela app dipersempit sistem."
        )
    ),
    ChangelogEntry(
        version = "v1.4.1",
        tagline = "VU Meter Compact, Ignore Short Tracks, dan Reel Animation sekarang beneran berefek -- plus pembersihan Settings.",
        notes = listOf(
            "Fix: \"Show VU Meter in Compact Mode\" sekarang benar-benar mengecilkan tampilan VU Meter di layar Pemutar -- sebelumnya cuma tersimpan tanpa efek visual.",
            "Fix: \"Ignore Short Audio Tracks (< 30s)\" sekarang benar-benar menyaring lagu berdurasi di bawah 30 detik dari hasil scan library.",
            "Fix: \"Enable Reel Spinning Animation\" sekarang benar-benar menghentikan animasi putaran roda kaset saat dimatikan.",
            "Kategori AUDIO & EFFECTS dan PLAYBACK dihapus dari Settings -- isinya (DSD Mode, Tape Hiss, Button Click, Wow/Flutter, Fade, Auto-pause, Peak Limiter, Gapless, ReplayGain, Audio Output Engine) ternyata tidak pernah benar-benar mengubah suara; kontrol audio yang sungguh nyata sudah ada lengkap di layar Equalizer (EQ, Vocal, Stereo, Limiter, Bypass).",
            "\"High-Performance DSP Threading\" juga dihapus dari System dengan alasan yang sama.",
            "Peak Limiter (Soft Clipping) tetap tersimpan permanen seperti biasa -- sekarang dikontrol dari satu tempat saja (tab BATAS di Equalizer)."
        )
    ),
    ChangelogEntry(
        version = "v1.4",
        tagline = "Pengaturan yang sungguh-sungguh berfungsi + Theme Accent Color nyata.",
        notes = listOf(
            "Theme Accent Color sekarang benar-benar mengubah warna aksen (border, ikon, teks emas) di seluruh app -- pilihan Gold Retro / Neon 80s / Silver Hi-Fi, tersimpan permanen.",
            "Semua toggle & dropdown di UI & Appearance, Playback, dan sebagian System sekarang tersimpan permanen -- sebelumnya diam-diam reset ke default tiap keluar dari layar Settings.",
            "Keep Screen Awake During Playback sekarang benar-benar mengunci layar, bukan cuma kosmetik.",
            "Menu Changelog ditambahkan di kategori System supaya riwayat perubahan bisa dilihat langsung dari dalam app."
        )
    ),
    ChangelogEntry(
        version = "v1.3",
        tagline = "Seek dengan memutar roda kaset + efek glitch pita rusak.",
        notes = listOf(
            "Roda kaset di layar Pemutar sekarang bisa diputar manual dengan jari untuk maju/mundur (seek), lengkap efek glitch chromatic split dan fast-spin dengan decay.",
            "Fix: tombol SHUFFLE ALL di Library sekarang ikut membuka layar Pemutar setelah memutar lagu.",
            "Fix: mematikan Shuffle sekarang kembali ke urutan album/playlist semula, bukan melompat ke seluruh isi library."
        )
    ),
    ChangelogEntry(
        version = "v1.2",
        tagline = "Playlist, export preset EQ, rescan manual, dan splash screen tanpa kedipan putih.",
        notes = listOf(
            "Fitur Playlist baru di tab Library: buat, isi, putar, dan hapus playlist sendiri.",
            "Export preset EQ aktif ke file .json untuk dibagikan/dipindahkan.",
            "\"Rescan Music Library Now\" -- pemindaian manual kapan saja tanpa perlu menutup-buka ulang app.",
            "Fix: layar putih polos saat app dibuka diganti splash screen gelap yang seragam dengan tampilan utama."
        )
    ),
    ChangelogEntry(
        version = "v1.0",
        tagline = "Rilis awal TapeAmp 32.",
        notes = listOf(
            "Pemutar kaset visual, Equalizer multi-band dengan preset custom + upload, Bass Boost/Virtualizer/Mono-Stereo, Library musik, import musik manual, Lyrics, dan kontrol transport lengkap (notifikasi, lockscreen, headset, Bluetooth, Android Auto)."
        )
    )
)

@Composable
internal fun ChangelogDialog(
    onDismiss: () -> Unit
) {

    Dialog(
        onDismissRequest = onDismiss
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PanelBlack)
                .border(
                    width = 1.dp,
                    color = StrokeGold,
                    shape = RoundedCornerShape(8.dp)
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            Text(
                text = stringResource(R.string.changelog_dialog_title),
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            changelogEntries.forEachIndexed { index, entry ->

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = entry.version,
                        color = GoldBright,
                        fontFamily = MonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    if (index == 0) {

                        Spacer(
                            modifier = Modifier.width(6.dp)
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Gold.copy(alpha = 0.12f))
                                .border(
                                    width = 0.7.dp,
                                    color = Gold,
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .padding(
                                    horizontal = 5.dp,
                                    vertical = 1.dp
                                )
                        ) {
                            Text(
                                text = stringResource(R.string.changelog_badge_latest),
                                color = Gold,
                                fontFamily = MonoFont,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = entry.tagline,
                    color = TextMuted,
                    fontFamily = DisplayFont,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(
                        top = 2.dp,
                        bottom = 6.dp
                    )
                )

                entry.notes.forEach { note ->
                    Row(
                        modifier = Modifier.padding(bottom = 5.dp)
                    ) {
                        Text(
                            text = "\u25B8 ",
                            color = Gold,
                            fontFamily = MonoFont,
                            fontSize = 10.sp
                        )
                        Text(
                            text = note,
                            color = TextLight,
                            fontFamily = DisplayFont,
                            fontSize = 10.sp
                        )
                    }
                }

                if (index != changelogEntries.lastIndex) {
                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(StrokeGold.copy(alpha = 0.5f))
                    )
                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )
                }
            }
        }
    }
}
