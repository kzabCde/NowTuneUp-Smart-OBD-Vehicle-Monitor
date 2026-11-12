package com.nowtuneup.app.ui.vehicle

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nowtuneup.app.data.obd.session.AdapterHealthState
import com.nowtuneup.app.domain.model.ConnectionUiState
import com.nowtuneup.app.presentation.theme.DaylightTheme
import com.nowtuneup.app.presentation.theme.GraphiteTheme
import com.nowtuneup.app.presentation.theme.NtuTheme

@Preview(name = "Garage · dark · 320dp", widthDp = 320, heightDp = 720)
@Composable
private fun GarageDarkPreview() { NtuTheme(GraphiteTheme, reduceMotion = true) { Surface { GarageContent(null, 0, ConnectionUiState(), AdapterHealthState(), {}, {}, {}, {}) } } }
@Preview(name = "Garage · light", widthDp = 393, heightDp = 800)
@Composable
private fun GarageLightPreview() { NtuTheme(DaylightTheme, reduceMotion = true) { Surface { GarageContent(null, 0, ConnectionUiState(), AdapterHealthState(), {}, {}, {}, {}) } } }
