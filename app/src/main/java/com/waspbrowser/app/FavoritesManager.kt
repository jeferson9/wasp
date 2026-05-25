package com.waspbrowser.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FavoritesManager {

    private const val PREF_NAME = "wasp_favorites"
    private const val KEY = "favorites"

    fun add(context: Context, title: String, url: String) {

        val list = getAll(context).toMutableList()

        // evitar duplicado
        if (list.any { it.url == url }) return

        list.add(Favorite(title, url))
        save(context, list)
    }

    fun remove(context: Context, url: String) {

        val list = getAll(context).toMutableList()

        list.removeAll { it.url == url }

        save(context, list)
    }

    fun getAll(context: Context): List<Favorite> {

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "[]") ?: "[]"

        val array = JSONArray(json)
        val list = mutableListOf<Favorite>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Favorite(
                    obj.getString("title"),
                    obj.getString("url")
                )
            )
        }

        return list
    }
    fun clear(context: Context) {

        save(context, emptyList())
    }
    private fun save(context: Context, list: List<Favorite>) {

        val array = JSONArray()

        list.forEach {
            val obj = JSONObject()
            obj.put("title", it.title)
            obj.put("url", it.url)
            array.put(obj)
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, array.toString()).apply()
    }
}
