package com.livingroomhq.core.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Central registry of widget plugins. Built-in widgets register at app start;
 * external plugin packages can be registered after discovery.
 */
class WidgetRegistry {

    private val _plugins = MutableStateFlow<List<WidgetPlugin>>(emptyList())
    val plugins: StateFlow<List<WidgetPlugin>> = _plugins.asStateFlow()

    fun register(plugin: WidgetPlugin) {
        _plugins.value = _plugins.value.filterNot { it.id == plugin.id } + plugin
    }

    fun unregister(id: String) {
        _plugins.value = _plugins.value.filterNot { it.id == id }
    }

    fun forZone(zone: WidgetZone) = plugins.map { list -> list.filter { zone in it.zones } }
}
