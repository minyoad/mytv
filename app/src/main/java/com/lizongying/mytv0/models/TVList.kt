package com.lizongying.mytv0.models

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonSyntaxException
import com.lizongying.mytv0.R
import com.lizongying.mytv0.showToast
import io.github.lizongying.Gua
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object TVList {
    private const val TAG = "TVList"
    private const val FILE_NAME = "channels.json"
    private lateinit var appDirectory: File
    private lateinit var serverUrl: String
    private lateinit var list: List<TV>
    var listModel: List<TVModel> = listOf()
    val groupModel = TVGroupModel()

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    fun init(context: Context) {
        _position.value = 0
        appDirectory = context.filesDir
        serverUrl = context.resources.getString(R.string.server_url)
        val file = File(appDirectory, FILE_NAME)
        val str = if (file.exists()) {
            Log.i(TAG, "local file")
            file.readText()
        } else {
            Log.i(TAG, "read resource")
            context.resources.openRawResource(R.raw.channels).bufferedReader()
                .use { it.readText() }
        }
        groupModel.addTVListModel(TVListModel("我的收藏"))

        groupModel.addTVListModel(TVListModel("全部频道"))

        try {
            str2List(str)
        } catch (e: Exception) {
            Log.e("", "error $e")
            file.deleteOnExit()
            Toast.makeText(context, "读取频道失败，请在菜单中进行设置", Toast.LENGTH_LONG).show()
        }

        update()
    }

    private fun update() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i("", "do request $serverUrl")
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(serverUrl).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val file = File(appDirectory, FILE_NAME)
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                    val str = response.body()!!.string()
                    Log.i("", "request str $str")

                    file.writeText(str)
                    withContext(Dispatchers.Main) {
                        str2List(str)
                    }
                    "频道读取成功".showToast()
                } else {
                    Log.e("", "request status ${response.code()}")
                    "频道状态错误".showToast()
                }
            } catch (e: JsonSyntaxException) {
                Log.e("JSON Parse Error", e.toString())
                "频道格式错误".showToast()
            } catch (e: NullPointerException) {
                Log.e("Null Pointer Error", e.toString())
                "无法读取频道".showToast()
            } catch (e: Exception) {
                Log.e("", "request error $e")
                "频道请求错误".showToast()
            }
        }
    }

    fun update(serverUrl: String) {
        this.serverUrl = serverUrl
        Log.i("", "update $serverUrl")
        update()
    }

    private fun findTvByName(name:String,tvlist:List<TV>):TV?{
        for (tv in tvlist){
            if (tv.title == name) {
                return tv
            }
        }
        return null
    }

    private fun str2List(str: String) {
        var string = str
        val g = Gua()
        if (g.verify(str)) {
            string = g.decode(str)
        }
        if (string.isBlank()) {
            return
        }
        Log.i("string", string)
        Log.i("string first", "${string[0]}")
        when (string[0]) {
            '[' -> {
                Log.i("", "1111111")
                val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
                list = com.google.gson.Gson().fromJson(string, type)
            }

            '#' -> {
                Log.i("", "2222222")
                val lines = string.lines()

                Log.i("lines", "$lines")
                val nameRegex = Regex("""tvg-name="([^"]+)"""")
                val logRegex = Regex("""tvg-logo="([^"]+)"""")
                val groupRegex = Regex("""group-title="([^"]+)"""")

                val l = mutableListOf<TV>()
                for ((index, line) in lines.withIndex()) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("#EXTINF")) {
                        val info = trimmedLine.split(",")
                        Log.i("info", "$info")

                        val title = info.last()
                        val name = nameRegex.find(info.first())?.groupValues?.get(1)
                        val group = groupRegex.find(info.first())?.groupValues?.get(1)
                        val logo = logRegex.find(info.first())?.groupValues?.get(1)
                        val uris =
                            if (index + 1 < lines.size) listOf(lines[index + 1]) else emptyList()
                        Log.i("info", "$title $name $group $logo $uris")
                        val tv = TV(
                            0,
                            name!!,
                            title,
                            "",
                            logo!!,
                            "",
                            uris,
                            mapOf(),
                            group!!,
                            listOf(),
                        )

                        l.add(tv)
                    }
                }
                list = l
            }

            else -> {
                Log.i("", "333333")
                val lines = string.lines()
                var group = ""
                val l = mutableListOf<TV>()
                for (line in lines) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.isNotEmpty()) {
                        if (trimmedLine.contains("#genre#")) {
                            group = trimmedLine.split(',', limit = 2)[0].trim()
                        } else {
                            val arr = trimmedLine.split(',').map { it.trim() }
                            var name=arr.first()
                            var url=arr.drop(1)

                            var tv=findTvByName(name,l)
                            if (tv==null) {
                                tv = TV(
                                    0,
                                    arr.first(),
                                    arr.first(),
                                    "",
                                    "",
                                    "",
                                    arr.drop(1),
                                    mapOf(),
                                    group,
                                    listOf(),
                                )

                                l.add(tv)
                            }else {
                                var uris = mutableListOf<String>()
                                tv.uris.forEach { u ->
                                    run {
                                        uris.add(u)
                                    }
                                }
                                uris.add(url.first())
                                tv.uris = uris.toList()

                            }

                        }
                    }
                }
                list = l
            }
        }

        Log.i("TVList", "$list")

        listModel = list.map { tv ->
            TVModel(tv)
        }
        setPosition(0)

        groupModel.clear()

        // 全部频道
        groupModel.getTVListModel(1)?.setTVListModel(listModel)

        val map: MutableMap<String, MutableList<TVModel>> = mutableMapOf()
        for ((id, v) in list.withIndex()) {
            if (v.group !in map) {
                map[v.group] = mutableListOf()
            }
            v.id = id
            map[v.group]?.add(TVModel(v))
        }

        for ((k, v) in map) {
            val tvListModel = TVListModel(k)
            for (v1 in v) {
                tvListModel.addTVModel(v1)
            }
            groupModel.addTVListModel(tvListModel)
        }
    }

    fun getTVModelCurrent(): TVModel {
        return getTVModel(position.value!!)
    }

    fun getTVModel(idx: Int): TVModel {
        return listModel[idx]
    }

    fun setPosition(position: Int): Boolean {
        if (position >= size()) {
            return false
        }

        if (_position.value != position) {
            _position.value = position
        }

        // set a new position or retry when position same
        listModel[position].setReady()
        return true
    }

    fun size(): Int {
        return listModel.size
    }
}