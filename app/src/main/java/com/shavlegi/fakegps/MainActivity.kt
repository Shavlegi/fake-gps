package com.shavlegi.fakegps

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shavlegi.fakegps.ui.theme.FakeGpsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/** Akhaltsikhe — home. The map opens here on first launch. */
private val HOME_POINT = GeoPoint(41.8458, 42.9996)

class FakeGpsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FavoritesStore(application)

    private val _picked = MutableStateFlow<GeoPoint?>(null)
    val picked: StateFlow<GeoPoint?> = _picked.asStateFlow()

    private val _favorites = MutableStateFlow<List<FavoritePlace>>(emptyList())
    val favorites: StateFlow<List<FavoritePlace>> = _favorites.asStateFlow()

    private val _searchResults = MutableStateFlow<List<NominatimResult>>(emptyList())
    val searchResults: StateFlow<List<NominatimResult>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _isMockApp = MutableStateFlow(false)
    val isMockApp: StateFlow<Boolean> = _isMockApp.asStateFlow()

    private val _centerOn = MutableStateFlow<GeoPoint?>(null)
    val centerOn: StateFlow<GeoPoint?> = _centerOn.asStateFlow()

    private val _language = MutableStateFlow("ka")
    val language: StateFlow<String> = _language.asStateFlow()

    val mockState: StateFlow<MockState> = MockLocationState.state

    init {
        viewModelScope.launch {
            store.favorites.collect { _favorites.value = it }
        }
        viewModelScope.launch {
            val last = store.lastPoint.first()
            if (last != null) _picked.value = GeoPoint(last.latitude, last.longitude)
        }
        viewModelScope.launch {
            store.language.collect { code ->
                if (code != null) _language.value = code
            }
        }
        refreshMockAppStatus()
    }

    /** Checks whether this app is selected as the mock location app. */
    fun refreshMockAppStatus() {
        val app = getApplication<Application>()
        val selected = Settings.Secure.getString(app.contentResolver, "mock_location")
        _isMockApp.value = selected == app.packageName
    }

    fun pick(point: GeoPoint) {
        _picked.value = point
        viewModelScope.launch {
            store.saveLastPoint(LastPoint(point.latitude, point.longitude))
        }
    }

    fun centerOn(point: GeoPoint) {
        _centerOn.value = point
    }

    fun consumeCenter() {
        _centerOn.value = null
    }

    fun search(query: String) {
        viewModelScope.launch {
            _searching.value = true
            _searchResults.value = try {
                NominatimClient.search(query)
            } catch (e: Exception) {
                emptyList()
            }
            _searching.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun startMocking(context: android.content.Context) {
        val point = _picked.value ?: return
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LAT, point.latitude)
            putExtra(MockLocationService.EXTRA_LNG, point.longitude)
        }
        context.startForegroundService(intent)
    }

    fun stopMocking(context: android.content.Context) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun addFavorite(name: String) {
        val point = _picked.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            store.saveFavorites(
                _favorites.value + FavoritePlace(trimmed, point.latitude, point.longitude)
            )
        }
    }

    fun removeFavorite(place: FavoritePlace) {
        viewModelScope.launch {
            store.saveFavorites(_favorites.value - place)
        }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch { store.saveLanguage(code) }
        _language.value = code
        LocaleHelper.applyLanguage(code)
    }
}

class FakeGpsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FakeGpsViewModel(application) as T
    }
}

class MainActivity : AppCompatActivity() {

    private var pendingAfterPermission: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // If location was just granted and a Start was waiting, run it now.
        if (grants.values.any { it }) {
            pendingAfterPermission?.invoke()
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_permission_needed),
                Toast.LENGTH_LONG
            ).show()
        }
        pendingAfterPermission = null
    }

    /** Runs [onGranted] now if a location permission is granted, otherwise
     *  asks for it first and runs it when the user allows. */
    fun ensureLocationPermission(onGranted: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onGranted()
            return
        }
        pendingAfterPermission = onGranted
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        requestLocationPermission()
        setContent {
            FakeGpsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FakeGpsScreen()
                }
            }
        }
    }

    private fun requestLocationPermission() {
        val needed = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@Composable
fun FakeGpsScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: FakeGpsViewModel = viewModel(
        factory = FakeGpsViewModelFactory(application)
    )

    val picked by viewModel.picked.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val isMockApp by viewModel.isMockApp.collectAsState()
    val mockState by viewModel.mockState.collectAsState()
    val language by viewModel.language.collectAsState()
    val centerOn by viewModel.centerOn.collectAsState()

    var query by remember { mutableStateOf("") }
    var showSetup by remember { mutableStateOf(false) }
    var showAddFavorite by remember { mutableStateOf(false) }
    var favoriteToDelete by remember { mutableStateOf<FavoritePlace?>(null) }
    var showLangMenu by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    val isMocking = mockState is MockState.Mocking

    // Re-check the mock-app selection every time the user comes back
    // (e.g. after visiting Developer options).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshMockAppStatus()
    }

    // Center the map when a search result or a favorite is chosen.
    LaunchedEffect(centerOn) {
        val target = centerOn ?: return@LaunchedEffect
        mapViewRef?.controller?.animateTo(target)
        viewModel.consumeCenter()
    }

    // Show the failure reason if mocking could not start.
    LaunchedEffect(mockState) {
        val error = mockState as? MockState.Error ?: return@LaunchedEffect
        val textRes = when (error.messageKey) {
            "location_permission" -> R.string.location_permission_needed
            else -> R.string.mock_failed
        }
        Toast.makeText(context, context.getString(textRes), Toast.LENGTH_LONG).show()
        viewModel.refreshMockAppStatus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            // Language picker: shows the current language code.
            Box {
                OutlinedButton(onClick = { showLangMenu = true }) {
                    Text(language.uppercase())
                }
                DropdownMenu(
                    expanded = showLangMenu,
                    onDismissRequest = { showLangMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("ქართული") },
                        onClick = {
                            viewModel.setLanguage("ka")
                            showLangMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("English") },
                        onClick = {
                            viewModel.setLanguage("en")
                            showLangMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Русский") },
                        onClick = {
                            viewModel.setLanguage("ru")
                            showLangMenu = false
                        }
                    )
                }
            }
            IconButton(onClick = { showSetup = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.setup_title),
                    tint = Color.White
                )
            }
        }

        // ---- Search ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.search(query) }
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.search(query) },
                enabled = query.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = Color.White
                )
            }
        }
        if (searching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (searchResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(horizontal = 12.dp)
            ) {
                items(searchResults) { result ->
                    Text(
                        text = result.display_name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val point = GeoPoint(
                                    result.lat.toDouble(),
                                    result.lon.toDouble()
                                )
                                viewModel.pick(point)
                                viewModel.centerOn(point)
                                viewModel.clearSearch()
                                query = result.display_name
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        } else if (!searching && query.isNotBlank() && searchResults.isEmpty()) {
            // Nothing to show — the user just hasn't searched yet, or the
            // search returned nothing. Keep it quiet; no error spam.
        }

        // ---- Mock-app warning ----
        if (!isMockApp) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF7A2E0E)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.mock_not_selected_warning),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            )
                        }
                    ) {
                        Text(stringResource(R.string.open_dev_options))
                    }
                }
            }
        }

        // ---- Map ----
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(picked ?: HOME_POINT)
                        overlays.add(
                            MapEventsOverlay(object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    viewModel.pick(p)
                                    return true
                                }

                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            })
                        )
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    mapView.overlays
                        .filterIsInstance<Marker>()
                        .toList()
                        .forEach { mapView.overlays.remove(it) }
                    picked?.let { point ->
                        val marker = Marker(mapView).apply {
                            position = point
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "%.5f, %.5f".format(point.latitude, point.longitude)
                        }
                        mapView.overlays.add(marker)
                    }
                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
            if (picked == null) {
                Text(
                    text = stringResource(R.string.tap_map_hint),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(
                            color = Color(0xCC0B0F19),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // ---- Bottom panel ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            picked?.let { point ->
                Text(
                    text = stringResource(R.string.picked_point) + ": " +
                        "%.5f, %.5f".format(point.latitude, point.longitude),
                    color = Color.White
                )
            }
            if (mockState is MockState.Mocking) {
                val m = mockState as MockState.Mocking
                Text(
                    text = stringResource(R.string.current_fake_location) + ": " +
                        "%.5f, %.5f".format(m.latitude, m.longitude),
                    color = Color(0xFF7CFC9A),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isMocking) {
                            viewModel.stopMocking(context)
                        } else if (picked == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.need_point_first),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Location permission is required for the
                            // foreground service — ask first, then start.
                            (context as? MainActivity)?.ensureLocationPermission {
                                viewModel.startMocking(context)
                            } ?: viewModel.startMocking(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMocking) {
                            Color(0xFFB3261E)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text(
                        text = if (isMocking) {
                            stringResource(R.string.stop)
                        } else {
                            stringResource(R.string.start)
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = { showAddFavorite = true },
                    enabled = picked != null,
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add_favorite))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.favorites),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_favorites),
                    color = Color(0xFF9AA5B1)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(favorites) { fav ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fav.name,
                                    color = Color.White,
                                    modifier = Modifier.clickable {
                                        val point = GeoPoint(fav.latitude, fav.longitude)
                                        viewModel.pick(point)
                                        viewModel.centerOn(point)
                                    }
                                )
                                IconButton(
                                    onClick = { favoriteToDelete = fav }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = Color(0xFFFF6B6B)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_limits_text),
                color = Color(0xFF9AA5B1),
                fontSize = 12.sp
            )
        }
    }

    // ---- Dialogs ----
    if (showSetup) {
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text(stringResource(R.string.setup_title)) },
            text = { Text(stringResource(R.string.setup_steps)) },
            confirmButton = {
                Button(
                    onClick = {
                        showSetup = false
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        )
                    }
                ) {
                    Text(stringResource(R.string.open_dev_options))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetup = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddFavorite) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFavorite = false },
            title = { Text(stringResource(R.string.add_favorite)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.favorite_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addFavorite(name)
                        showAddFavorite = false
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFavorite = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    favoriteToDelete?.let { fav ->
        AlertDialog(
            onDismissRequest = { favoriteToDelete = null },
            title = { Text(fav.name) },
            text = { Text(stringResource(R.string.delete_favorite_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeFavorite(fav)
                        favoriteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB3261E)
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { favoriteToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
