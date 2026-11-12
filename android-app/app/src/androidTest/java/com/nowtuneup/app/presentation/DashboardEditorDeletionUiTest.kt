package com.nowtuneup.app.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.data.dashboard.DashboardRepository
import com.nowtuneup.app.data.local.database.NtuDatabase
import com.nowtuneup.app.domain.model.DashboardConfig
import com.nowtuneup.app.presentation.theme.GraphiteTheme
import com.nowtuneup.app.presentation.theme.NtuTheme
import com.nowtuneup.app.ui.dashboard.editor.DashboardEditor
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardEditorDeletionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deletingOneWidgetRequiresConfirmationAndRemainsUndoableBeforeSaving() {
        val original = profile()
        var saved: DashboardConfig? = null
        showEditor(original) { saved = it }

        openTargetWidget()
        compose.onNodeWithText("ลบ Widget").performScrollTo().performClick()
        dialogButton("ลบ Widget นี้?", "ยกเลิก").performClick()
        compose.onNodeWithText("ปิด").performScrollTo().performClick()
        save()
        assertEquals(original.portrait.widgets.map { it.id }, checkNotNull(saved).portrait.widgets.map { it.id })

        openTargetWidget()
        compose.onNodeWithText("ลบ Widget").performScrollTo().performClick()
        dialogButton("ลบ Widget นี้?", "ลบ Widget").performClick()
        save()
        assertEquals(listOf("survivor"), checkNotNull(saved).portrait.widgets.map { it.id })
        assertEquals(original.landscape, checkNotNull(saved).landscape)

        compose.onNodeWithText("ย้อนกลับ").performClick()
        save()
        assertEquals(original.portrait.widgets.map { it.id }, checkNotNull(saved).portrait.widgets.map { it.id })
        compose.onNodeWithText("ทำซ้ำ").performClick()
        save()
        assertEquals(listOf("survivor"), checkNotNull(saved).portrait.widgets.map { it.id })
        assertSurvivesDatabaseReopen(checkNotNull(saved))
    }

    @Test fun clearingCanvasRequiresConfirmationAndPersistsAnEmptyActiveOrientation() {
        val original = profile()
        var saved: DashboardConfig? = null
        showEditor(original) { saved = it }

        clearCanvas()
        dialogButton("ล้าง Widget ในหน้านี้?", "ยกเลิก").performClick()
        save()
        assertEquals(original.portrait.widgets.map { it.id }, checkNotNull(saved).portrait.widgets.map { it.id })

        clearCanvas()
        dialogButton("ล้าง Widget ในหน้านี้?", "ล้าง Widget").performClick()
        save()
        assertTrue(checkNotNull(saved).portrait.widgets.isEmpty())
        assertEquals(original.landscape, checkNotNull(saved).landscape)
        compose.onNodeWithText("ย้อนกลับ").performClick()
        save()
        assertEquals(original.portrait.widgets.map { it.id }, checkNotNull(saved).portrait.widgets.map { it.id })
        compose.onNodeWithText("ทำซ้ำ").performClick()
        save()
        assertTrue(checkNotNull(saved).portrait.widgets.isEmpty())
        assertSurvivesDatabaseReopen(checkNotNull(saved))
    }

    private fun profile(): DashboardConfig {
        val base = DashboardDefaults.newProfile(id = "editor-deletion", name = "Deletion test")
        val target = DashboardDefaults.widgetCatalog.first { it.pid == 0x0D }
            .copy(id = "target", title = "Editor target")
        val survivor = target.copy(id = "survivor", title = "Editor survivor", canvasX = 6)
        return base.copy(
            portrait = base.portrait.copy(widgets = listOf(target, survivor)),
            landscape = base.landscape.copy(widgets = listOf(target.copy(title = "Landscape target"))),
        )
    }

    private fun showEditor(config: DashboardConfig, onSave: (DashboardConfig) -> Unit) {
        compose.setContent {
            NtuTheme(GraphiteTheme, reduceMotion = true) {
                DashboardEditor(config, drivingMode = false, onSave = onSave, onCancel = {})
            }
        }
    }

    private fun openTargetWidget() {
        val targetButton = hasText("Editor target") and hasClickAction() and hasAnySibling(hasText("ขึ้น"))
        compose.onNodeWithTag("hud-editor-list").performScrollToNode(targetButton)
        compose.onNode(targetButton).performClick()
        compose.onNodeWithText("Widget Properties").assertIsDisplayed()
    }

    private fun clearCanvas() {
        compose.onNodeWithTag("hud-editor-list").performScrollToNode(hasText("ล้าง Canvas"))
        compose.onNodeWithText("ล้าง Canvas").performClick()
    }

    private fun dialogButton(title: String, label: String) = compose.onNode(
        hasText(label) and hasAnyAncestor(isDialog() and hasAnyDescendant(hasText(title))),
    )

    private fun save() {
        compose.onNodeWithText("บันทึก").performClick()
        compose.waitForIdle()
    }

    private fun assertSurvivesDatabaseReopen(saved: DashboardConfig): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "editor-deletion-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, NtuDatabase::class.java, name).build()
        var database = open()
        try {
            DashboardRepository(database.dao()).save(saved)
            database.close()
            database = open()
            val restored = DashboardRepository(database.dao()).workspace.first().dashboards.single()
            assertEquals(saved, restored)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
