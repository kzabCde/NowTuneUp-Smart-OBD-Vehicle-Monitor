package com.nowtuneup.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nowtuneup.app.domain.model.*
import com.nowtuneup.app.presentation.dashboard.MainViewModel
import com.nowtuneup.app.presentation.theme.NtuTheme
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{NtuTheme{NtuApp()}}}}
data class Destination(val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
@Composable fun NtuApp(vm:MainViewModel=hiltViewModel()){
 val destinations=listOf(Destination("Dashboard",Icons.Default.Speed),Destination("Live Data",Icons.Default.List),Destination("Diagnostics",Icons.Default.Warning),Destination("Trips",Icons.Default.Route),Destination("Settings",Icons.Default.Settings));var selected by remember{mutableIntStateOf(0)};val state by vm.connection.collectAsState();val error by vm.error.collectAsState()
 Scaffold(topBar={TopAppBar(title={Column{Text("NTU",fontWeight=FontWeight.Black);Text("Vehicle Monitoring",fontSize=11.sp)}},actions={AssistChip(onClick=vm::toggleConnection,label={Text(state.name.replace('_',' '))},leadingIcon={Icon(if(state==ConnectionState.CONNECTED)Icons.Default.CheckCircle else Icons.Default.Usb,"Connection")})})},bottomBar={NavigationBar{destinations.forEachIndexed{i,d->NavigationBarItem(selected==i,{selected=i},{Icon(d.icon,d.title)},{Text(d.title,fontSize=10.sp)})}}}){padding->Box(Modifier.padding(padding)){when(selected){0->Dashboard(vm);1->LiveData(vm);2->Diagnostics(vm);3->Trips(vm);else->Settings()}}}
 error?.let{AlertDialog(onDismissRequest=vm::dismissError,confirmButton={TextButton(onClick=vm::dismissError){Text("OK")}},title={Text("Communication error")},text={Text(it)})}
}
@Composable fun Dashboard(vm:MainViewModel){val readings by vm.readings.collectAsState();val landscape=LocalConfiguration.current.screenWidthDp>LocalConfiguration.current.screenHeightDp;LazyVerticalGrid(columns=GridCells.Fixed(if(landscape)3 else 2),contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(readings.filter{it.pid in listOf(0x0C,0x0D,0x05,0x42,0x04,0x11)}){GaugeCard(it)}}}
@Composable fun GaugeCard(r:VehicleReading){Card(Modifier.height(if(r.pid in listOf(0x0C,0x0D))190.dp else 130.dp),shape=RoundedCornerShape(20.dp)){Box(Modifier.fillMaxSize().padding(12.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){drawArc(androidx.compose.ui.graphics.Color.DarkGray,145f,250f,false,style=Stroke(10f,cap=StrokeCap.Round));val ratio=if(r.value==null)0f else ((r.value-(r.minimum?:0.0))/((r.maximum?:100.0)-(r.minimum?:0.0))).toFloat().coerceIn(0f,1f);drawArc(androidx.compose.ui.graphics.Color.Cyan,145f,250f*ratio,false,style=Stroke(10f,cap=StrokeCap.Round))};Column(horizontalAlignment=Alignment.CenterHorizontally){Text(r.name,fontSize=12.sp);Text(r.value?.let{"%.1f".format(it)}?:"—",fontSize=32.sp,fontWeight=FontWeight.Bold);Text(if(r.supported)r.unit else "Not supported",fontSize=12.sp)}}}}
@Composable fun LiveData(vm:MainViewModel){val readings by vm.readings.collectAsState();var query by remember{mutableStateOf("")};Column{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(12.dp),label={Text("Search parameters")});Row{Button(vm::pause,Modifier.padding(start=12.dp)){Text("Pause")};TextButton(vm::resume){Text("Resume")}};LazyColumn{items(readings.filter{it.name.contains(query,true)}){r->ListItem(headlineContent={Text(r.name)},overlineContent={Text("PID 01%02X".format(r.pid))},supportingContent={Text(if(r.supported)"Range ${r.minimum}–${r.maximum}" else "Not supported")},trailingContent={Text(r.value?.let{"%.1f ${r.unit}".format(it)}?:"—")});HorizontalDivider()}}}}
@Composable fun Diagnostics(vm:MainViewModel){val dtcs by vm.dtcs.collectAsState();Column(Modifier.padding(16.dp)){Text("Stored diagnostic trouble codes",style=MaterialTheme.typography.headlineSmall);Text("Read-only scan. Descriptions may vary by manufacturer.");Button(vm::scan,Modifier.padding(vertical=12.dp)){Text("Scan stored DTCs")};if(dtcs.isEmpty())Text("No scan results") else dtcs.forEach{Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(14.dp)){Text(it.code,fontSize=24.sp,fontWeight=FontWeight.Bold);Text(it.description?:"Manufacturer-specific description unavailable");Text(it.status)}}}}}
@Composable fun Trips(vm:MainViewModel){val trips by vm.trips.collectAsState(emptyList());Column(Modifier.padding(16.dp)){Text("Trip history",style=MaterialTheme.typography.headlineSmall);Button(vm::toggleTrip,Modifier.padding(vertical=12.dp)){Text("Start / stop recording")};if(trips.isEmpty())Text("No recorded trips yet") else trips.forEach{ListItem(headlineContent={Text("Trip #${it.id}")},supportingContent={Text(java.util.Date(it.startTime).toString())})}}}
@Composable fun Settings(){Column(Modifier.padding(16.dp)){Text("Settings",style=MaterialTheme.typography.headlineSmall);listOf("Metric units","Keep screen awake","Automatic reconnection","Landscape dashboard","Debug information").forEach{var checked by remember{mutableStateOf(it!="Debug information")};ListItem(headlineContent={Text(it)},trailingContent={Switch(checked,{v->checked=v})})};Text("NTU 1.0.0 • Local-first • Read-only OBD-II",Modifier.padding(16.dp))}}
