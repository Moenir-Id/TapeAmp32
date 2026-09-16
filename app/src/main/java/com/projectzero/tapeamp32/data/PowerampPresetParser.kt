package com.projectzero.tapeamp32.data

import org.json.JSONArray
import org.json.JSONObject

object PowerampPresetParser {

    fun parse(json: String): List<EqPreset> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()

        val array: JSONArray = try {
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                JSONArray().put(JSONObject(trimmed))
            } else {
                return emptyList()
            }
        } catch (e: Exception) {
            return emptyList()
        }

        val presets = mutableListOf<EqPreset>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val bandsArr = obj.optJSONArray("bands") ?: JSONArray()
            val bands = mutableListOf<EqBand>()

            for (b in 0 until bandsArr.length()) {
                val bandObj = bandsArr.optJSONObject(b) ?: continue
                bands.add(
                    EqBand(
                        type = bandObj.optInt("type", 2),
                        channels = bandObj.optInt("channels", 0),
                        frequency = bandObj.optDouble("frequency", 1000.0),
                        q = bandObj.optDouble("q", 1.0),
                        gain = bandObj.optDouble("gain", 0.0),
                        color = bandObj.optInt("color", 0)
                    )
                )
            }

            presets.add(
                EqPreset(
                    name = obj.optString("name", "Imported Preset"),
                    preamp = obj.optDouble("preamp", 0.0),
                    parametric = obj.optBoolean("parametric", false),
                    bands = if (bands.isNotEmpty()) bands else flatTenBandPreset().bands
                )
            )
        }
        return presets
    }

    fun serialize(preset: EqPreset): String {
        val obj = JSONObject()
        obj.put("name", preset.name)
        obj.put("preamp", preset.preamp)
        obj.put("parametric", preset.parametric)

        val bandsArr = JSONArray()
        preset.bands.forEach { band ->
            val bandObj = JSONObject()
            bandObj.put("type", band.type)
            bandObj.put("channels", band.channels)
            bandObj.put("frequency", band.frequency)
            bandObj.put("q", band.q)
            bandObj.put("gain", band.gain)
            bandObj.put("color", band.color)
            bandsArr.put(bandObj)
        }

        obj.put("bands", bandsArr)
        val arr = JSONArray()
        arr.put(obj)
        return arr.toString(2)
    }

    fun serializeList(presets: List<EqPreset>): String {
        val arr = JSONArray()
        presets.forEach { preset ->
            val obj = JSONObject()
            obj.put("name", preset.name)
            obj.put("preamp", preset.preamp)
            obj.put("parametric", preset.parametric)

            val bandsArr = JSONArray()
            preset.bands.forEach { band ->
                val bandObj = JSONObject()
                bandObj.put("type", band.type)
                bandObj.put("channels", band.channels)
                bandObj.put("frequency", band.frequency)
                bandObj.put("q", band.q)
                bandObj.put("gain", band.gain)
                bandObj.put("color", band.color)
                bandsArr.put(bandObj)
            }
            obj.put("bands", bandsArr)
            arr.put(obj)
        }
        return arr.toString()
    }
}
