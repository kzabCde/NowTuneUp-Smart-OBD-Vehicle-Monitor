package com.nowtuneup.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.presentation.theme.*
import com.nowtuneup.app.ui.components.*

@Preview(name = "Graphite phone", widthDp = 360, heightDp = 740)
@Preview(name = "Large type", widthDp = 360, heightDp = 900, fontScale = 1.5f)
@Composable
private fun GraphitePreview() { DesignPreview(false) }

@Preview(name = "Daylight phone", widthDp = 360, heightDp = 740)
@Composable
private fun DaylightPreview() { DesignPreview(true) }

@Composable
private fun DesignPreview(light: Boolean) {
    NtuTheme(if (light) DaylightTheme else GraphiteTheme, reduceMotion = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                NtuScreenHeader("รถของฉัน", "เริ่มต้นการเดินทางกับ NowTuneUp")
                NtuEmptyState("ทุกการเดินทาง เริ่มจากรถของคุณ", "เพิ่มรถคันแรก แล้วจัดหน้าปัดสำหรับข้อมูลที่คุณอยากติดตาม",
                    Icons.Default.DirectionsCar, "เพิ่มรถคันแรก", {})
            }
        }
    }
}
