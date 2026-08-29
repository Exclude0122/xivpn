package cn.gov.xivpn2.ui

import android.net.DnsResolver
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cn.gov.xivpn2.R
import cn.gov.xivpn2.ui.ui.theme.XiVPNTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.internal.connection.RouteSelector.Companion.socketHost
import okio.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy


data class TimelineEntry(
    val title: String,
    val text: String? = null,
    val subtitle: String? = null,
    val error: Boolean = false
)

data class LatencyUIState(
    var running: Boolean = false,
    var timelineEntries: List<TimelineEntry> = listOf(),
)

data class DNSQueryResult(
    var result: List<InetAddress>? = null,
    var error: String? = null
)

class LatencyViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LatencyUIState())
    val uiState: StateFlow<LatencyUIState> = _uiState.asStateFlow()


    @RequiresApi(Build.VERSION_CODES.Q)
    fun start(url: String) {
        _uiState.update {
            it.copy(running = true, timelineEntries = listOf())
        }

        viewModelScope.launch(Dispatchers.IO) {

            val httpClient = OkHttpClient.Builder()
                .dns { hostname ->
                    var result: DNSQueryResult? = null

                    _uiState.update {
                        val newList = _uiState.value.timelineEntries + TimelineEntry(
                            "DNS Query",
                            "Querying A $hostname using system resolver..."
                        )
                        it.copy(timelineEntries = newList)
                    }

                    val startTime = System.currentTimeMillis()

                    runBlocking {


                        val channel = Channel<DNSQueryResult>()

                        val dnsResolver = DnsResolver.getInstance()


                        dnsResolver.query(
                            null,
                            hostname,
                            DnsResolver.TYPE_A,
                            DnsResolver.FLAG_NO_CACHE_STORE,
                            Dispatchers.IO.asExecutor(),
                            null, object : DnsResolver.Callback<List<InetAddress>> {
                                override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                                    if (rcode == 0) {
                                        runBlocking { channel.send(DNSQueryResult(result = answer)) }
                                    } else {
                                        runBlocking { channel.send(DNSQueryResult(error = "Error: rcode = $rcode")) }
                                    }
                                }

                                override fun onError(error: DnsResolver.DnsException) {
                                    runBlocking { channel.send(DNSQueryResult(error = "Error: " + error.javaClass.name + ": " + error.message)) }
                                }
                            }
                        )

                        result = channel.receive()
                    }

                    if (result!!.error != null) {
                        throw IOException("dns query failed: " + result.error)
                    }

                    val sb = StringBuilder()
                    for (address in result.result!!) {
                        sb.append(address.hostAddress).append("\n")
                    }

                    _uiState.update {
                        val newList = _uiState.value.timelineEntries + TimelineEntry(
                            "DNS Result",
                            result.result!!.map { a -> a.hostAddress }.joinToString("\n"),
                            (System.currentTimeMillis() - startTime).toString() + "ms"
                        )
                        it.copy(timelineEntries = newList)
                    }

                    result.result!!
                }
                .eventListener(object : EventListener() {

                    val connectStartTime: MutableMap<InetSocketAddress, Long> = HashMap()
                    var secureConnectStartTime: Long = 0
                    var requestStartTime: Long = 0
                    var requestSentTime: Long = 0

                    override fun connectStart(
                        call: Call,
                        inetSocketAddress: InetSocketAddress,
                        proxy: Proxy
                    ) {
                        connectStartTime[inetSocketAddress] = System.currentTimeMillis()

                        _uiState.update {
                            val newList = _uiState.value.timelineEntries + TimelineEntry(
                                "TCP Connect",
                                "Connecting to " + inetSocketAddress.address.hostAddress + ":" + inetSocketAddress.port +  "...",
                            )
                            it.copy(timelineEntries = newList)
                        }
                    }

                    override fun connectEnd(
                        call: Call,
                        inetSocketAddress: InetSocketAddress,
                        proxy: Proxy,
                        protocol: Protocol?
                    ) {
                        _uiState.update {
                            val newList = _uiState.value.timelineEntries + TimelineEntry(
                                "TCP Connected",
                                "Connected to " + inetSocketAddress.address.hostAddress + ":" + inetSocketAddress.port,
                                (System.currentTimeMillis() - (connectStartTime[inetSocketAddress] ?: 0)).toString() + "ms"
                            )
                            it.copy(timelineEntries = newList)
                        }
                    }

                    override fun secureConnectStart(call: Call) {
                        secureConnectStartTime = System.currentTimeMillis()
                        _uiState.update {
                            val newList = _uiState.value.timelineEntries + TimelineEntry(
                                "TLS Handshaking",
                            )
                            it.copy(timelineEntries = newList)
                        }
                    }

                    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
                        _uiState.update {
                            val newList = _uiState.value.timelineEntries + TimelineEntry(
                                "TLS Handshake Done",
                                subtitle = (System.currentTimeMillis() - secureConnectStartTime).toString() + "ms"
                            )
                            it.copy(timelineEntries = newList)
                        }
                    }

                    override fun requestHeadersStart(call: Call) {
                        _uiState.update {
                            val newList = _uiState.value.timelineEntries + TimelineEntry(
                                "Sending Request",
                            )
                            it.copy(timelineEntries = newList)
                        }
                        requestStartTime = System.currentTimeMillis()
                    }

                    override fun requestHeadersEnd(call: Call, request: Request) {
                        _uiState.update {
                            val newList = _uiState.value.timelineEntries + TimelineEntry(
                                "Request Sent",
                                request.method + " " + request.url.encodedPath,
                                subtitle = (System.currentTimeMillis() - requestStartTime).toString() + "ms"
                            )
                            it.copy(timelineEntries = newList)
                        }
                        requestSentTime = System.currentTimeMillis()
                    }

                    override fun responseBodyEnd(call: Call, byteCount: Long) {
                        _uiState.update {
                            val newList = _uiState.value.timelineEntries + TimelineEntry(
                                "Response Received",
                                subtitle = (System.currentTimeMillis() - requestSentTime).toString() + "ms"
                            )
                            it.copy(timelineEntries = newList)
                        }
                    }


                })
                .build()

            try {
                val start = System.currentTimeMillis()
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()

                val sb = StringBuilder()
                sb.append(response.protocol).append(" ").append(response.code).append(" ").append(response.message).append("\n")
                for (pair in response.headers) {
                    sb.append(pair.first).append(": ").append(pair.second).append("\n")
                }

                _uiState.update {
                    val newList = _uiState.value.timelineEntries + TimelineEntry(
                        "HTTP Roudntrip Completed",
                        sb.toString(),
                        subtitle = (System.currentTimeMillis() - start).toString() + "ms"
                    )
                    it.copy(timelineEntries = newList)
                }

            } catch (e: Exception) {
                Log.e("LatencyActivity", "okhttp", e)

                _uiState.update {
                    val newList = _uiState.value.timelineEntries + TimelineEntry(
                        "Error",
                        e.javaClass.simpleName + ": " + e.message,
                        error = true
                    )
                    it.copy(timelineEntries = newList)
                }
            }


            _uiState.update {
                it.copy(running = false)
            }
        }
    }

    fun stop() {
        _uiState.update {
            it.copy(running = false)
        }

    }
}


@Composable
fun Timeline(entries: List<TimelineEntry>) {
    Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (entry in entries) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors().copy(containerColor = if (entry.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(8.dp)) {

                    Row (verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(entry.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        if (entry.subtitle != null) {
                            Card(colors = CardDefaults.cardColors().copy(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Text(entry.subtitle, modifier = Modifier.padding(2.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }

                    if (entry.text != null) Text(entry.text, fontFamily = FontFamily.Monospace)


                }
            }
        }
    }

}

@Preview
@Composable
fun TimelinePreview() {
    Box(modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)) {
        Timeline(listOf(
            TimelineEntry("DNS Query", "Started DNS Query..."),
            TimelineEntry("DNS Result", "DNS Query Result:\n8.8.8.8", "10ms"),

        ))
    }

}


class LatencyActivity : ComponentActivity() {
    private val viewModel: LatencyViewModel by lazy {
        ViewModelProvider(this)[LatencyViewModel::class.java]
    }


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.dns_requires_api_29)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> finish() }
                .show()
            return
        }

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            var url by remember { mutableStateOf("https://google.com/generate_204") }
            val scrollState = rememberScrollState()


            XiVPNTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            title = {
                                Text(getString(R.string.latency_test))
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material),
                                        contentDescription = "Localized description"
                                    )
                                }
                            },
                        )
                    },
                ) { innerPadding ->

                    Column (modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                    ) {

                        if (uiState.value.running) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Butt, gapSize = 0.dp)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth()) {

                            if (!uiState.value.running) {
                                TextField(modifier = Modifier.fillMaxWidth(), value = url, onValueChange = { n -> url = n}, label = { Text(getString(R.string.url)) })
                                Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.start(url) }) { Text(getString(R.string.start)) }
                            } else {
                                Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.stop() }) { Text(getString(R.string.cancel)) }
                            }

                            Timeline(uiState.value.timelineEntries)

                        }
                    }



                }
            }
        }

    }

}
