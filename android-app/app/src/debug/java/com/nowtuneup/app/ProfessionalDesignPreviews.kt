package com.nowtuneup.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nowtuneup.app.presentation.theme.*
import com.nowtuneup.app.ui.components.*
import com.nowtuneup.app.ui.navigation.*

@Preview(name = "Professional · dark · 320dp", widthDp = 320, heightDp = 720)
@Preview(name = "Professional · landscape", widthDp = 800, heightDp = 400)
@Composable private fun ProfessionalDarkPreview() { ProfessionalPreview(false) }
@Preview(name = "Professional · light · large type", widthDp = 393, heightDp = 800, fontScale = 1.5f)
@Composable private fun ProfessionalLightPreview() { ProfessionalPreview(true) }

@Composable private fun ProfessionalPreview(light: Boolean) {
    NtuTheme(if(light) DaylightTheme else GraphiteTheme, reduceMotion = true) {
        Scaffold(bottomBar = { AppBottomNavigation(AppDestination.HOME, {}) }) { padding ->
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NtuScreenHeader("NowTuneUp", "ข้อมูลรถที่แม่นตรง · เก็บไว้ในอุปกรณ์")
                NtuPanel { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NtuSectionTitle("การเชื่อมต่อ", "ยังไม่เชื่อมต่ออะแดปเตอร์")
                    NtuMetricValue("—", "km/h")
                    Text("Unavailable · ไม่มีข้อมูลรถจำลอง", style = MaterialTheme.typography.bodySmall)
                } }
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("สร้างหน้าปัด") }
            }
        }
    }
}
