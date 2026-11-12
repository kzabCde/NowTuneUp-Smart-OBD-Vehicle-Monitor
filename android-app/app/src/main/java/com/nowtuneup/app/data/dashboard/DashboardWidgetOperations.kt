package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.domain.model.DashboardLayout
import com.nowtuneup.app.domain.model.DashboardWidgetType

/** Editing changes layout references only; telemetry and saved sessions are never touched. */
fun DashboardLayout.removeWidget(id: String): DashboardLayout = copy(widgets = widgets.filterNot { it.id == id })

fun DashboardLayout.moveWidget(id: String, offset: Int): DashboardLayout {
    val ordered = widgets.sortedBy { it.zIndex }.toMutableList()
    val index = ordered.indexOfFirst { it.id == id }
    if (index < 0) return this
    val target = (index + offset).coerceIn(0, ordered.lastIndex)
    if (index == target) return this
    ordered.add(target, ordered.removeAt(index))
    return copy(widgets = ordered.mapIndexed { position, widget -> widget.copy(zIndex = position) })
}

fun DashboardLayout.resizeWidget(id: String, width: Int, height: Int): DashboardLayout = copy(widgets = widgets.map { widget ->
    if (widget.id != id || widget.locked) widget else {
        val w = width.coerceIn(minOf(3, columns), columns)
        val h = height.coerceIn(minOf(if(widget.type == DashboardWidgetType.SHIFT_LIGHT) 2 else 3, rows), rows)
        widget.copy(canvasWidth = w, canvasHeight = h,
            canvasX = widget.canvasX.coerceIn(0, columns-w), canvasY = widget.canvasY.coerceIn(0, rows-h))
    }
})
