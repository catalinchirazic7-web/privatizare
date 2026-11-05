package com.example.privatizare.ui.game

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.privatizare.logic.STARTING_MONEY_STEP
import com.example.privatizare.ui.home.RulesScreen
import com.example.privatizare.logic.maxCustomStartingMoney
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.activity.compose.BackHandler
import com.example.privatizare.multiplayer.FirebaseLobbyBridge
import com.example.privatizare.multiplayer.OnlineConfig
import com.example.privatizare.multiplayer.OnlineController
import com.example.privatizare.ui.online.LobbyScreen
import com.example.privatizare.ui.online.OnlineMenuScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.unit.dp
import com.example.privatizare.R
import com.example.privatizare.ui.video.VideoScreen
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.app.Activity
import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Tile

private enum class Screen { INTRO, HOME, SETUP, ONLINE_MENU, LOBBY, GAME, OUTRO, RULES }

/**
 * Rădăcina navigării: intro -> ecran principal -> configurare -> joc -> outro.
 * Outro-ul închide activitatea, deci aplicația se termină după el.
 */
@Composable
fun AppRoot(
    catalog: GameRepository.Catalog,
    home: @Composable (
        onNewGame: () -> Unit,
        onOnline: () -> Unit,
        onExit: () -> Unit,
        canResume: Boolean,
        onResume: () -> Unit,
        onRules: () -> Unit,
    ) -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.INTRO) }
    val appContext = LocalContext.current
    // viewModel() păstrează starea partidei între recompuneri și schimbări de configurare
    val vm: GameViewModel = viewModel(
        factory = viewModelFactory { initializer { GameViewModel(catalog) } },
    )
    val activity = LocalContext.current as? Activity
    var online by remember { mutableStateOf<OnlineController?>(null) }

    // Camera creată/intrată -> lobby; meci pornit -> joc
    val inRoom = online?.isInRoom == true
    val inOnlineGame = online?.inGame == true
    LaunchedEffect(inRoom, inOnlineGame) {
        if (inOnlineGame) screen = Screen.GAME
        else if (inRoom && screen == Screen.ONLINE_MENU) screen = Screen.LOBBY
        else if (!inRoom && screen == Screen.LOBBY) screen = Screen.HOME
    }

    // Butonul „înapoi” al telefonului: în meniuri ne întoarce la ecranul principal,
    // ca să poți schimba jocul (cu roboți -> online) fără să închizi aplicația.
    BackHandler(enabled = screen == Screen.SETUP || screen == Screen.ONLINE_MENU || screen == Screen.LOBBY) {
        if (screen == Screen.ONLINE_MENU || screen == Screen.LOBBY) online?.leave()
        screen = Screen.HOME
    }
    // Gazda publică starea oficială la fiecare schimbare
    val hosting = online?.isHost == true && inOnlineGame
    LaunchedEffect(hosting) {
        val c = online
        if (hosting && c != null) {
            snapshotFlow { vm.snapshot() }.collect { c.publishNow() }
        }
    }

    when (screen) {
        Screen.INTRO -> VideoScreen(rawResId = R.raw.intro_video) { screen = Screen.HOME }
        Screen.HOME -> home(
            { screen = Screen.SETUP },
            {
                // Conexiunea la Firebase se creează doar când intri în jocul online
                if (online == null) {
                    online = runCatching {
                        OnlineController(FirebaseLobbyBridge(OnlineConfig.DATABASE_URL.ifBlank { null }), vm)
                    }.getOrNull()
                }
                screen = if (online != null) Screen.ONLINE_MENU else Screen.HOME
            },
            { screen = Screen.OUTRO },
            remember(screen) { SaveStore.load(appContext) != null },
            {
                SaveStore.load(appContext)?.let {
                    vm.restore(it.snapshot, it.leftMs)
                    screen = Screen.GAME
                }
            },
            { screen = Screen.RULES },
        )
        Screen.RULES -> RulesScreen(onBack = { screen = Screen.HOME })
        Screen.ONLINE_MENU -> online?.let { c ->
            OnlineMenuScreen(c, onBack = { c.leave(); screen = Screen.HOME })
        }
        Screen.LOBBY -> online?.let { c ->
            LobbyScreen(c, onLeave = { c.leave(); screen = Screen.HOME })
        }
        Screen.SETUP -> SetupScreen(
            onStart = { setup, money, minutes, dice, layout ->
                vm.startGame(setup, money, minutes, dice, layout)
                screen = Screen.GAME
            },
            onBack = { screen = Screen.HOME },
        )
        // Din joc ieși în meniul principal (nu se închide aplicația); ieșirea reală
        // din aplicație rămâne butonul „Ieșire din joc” de pe ecranul principal.
        Screen.GAME -> GameScreen(
            vm = vm,
            catalog = catalog,
            onExit = {
                online?.leave()
                screen = Screen.HOME
            },
        )
        Screen.OUTRO -> VideoScreen(rawResId = R.raw.outro_video) { activity?.finish() }
    }
}

/** Configurare: 2–4 jucători, nume și culoare unică din paleta de 8. */
@Composable
fun SetupScreen(onStart: (List<PlayerSetup>, Long?, Int?, Int, String) -> Unit, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val saved = remember { SetupMemory.load(context) }
    var count by remember { mutableIntStateOf(saved?.count ?: 2) }
    // null = „Maxim”: tirajul se împarte egal; altfel fiecare jucător primește suma aleasă
    var startMoney by remember { mutableStateOf(saved?.money) }
    // null = fără limită de timp; altfel, minute
    var timeMinutes by remember { mutableStateOf(saved?.timeMinutes) }
    // 1 zar (implicit) sau 2 zaruri (doar local); tabla veche (implicit) sau nouă
    var dice by remember { mutableIntStateOf(saved?.dice ?: 1) }
    var layout by remember { mutableStateOf(saved?.layout ?: "old") }
    val names = remember { mutableStateListOf(*(saved?.names ?: listOf("Jucător 1", "Jucător 2", "Jucător 3", "Jucător 4")).toTypedArray()) }
    // Fiecare loc începe cu alt pion; poți schimba oricând (dacă alegi pionul altui loc, se fac schimb)
    val colors = remember {
        mutableStateListOf<PawnColor?>(*(saved?.colors?.map { c -> c?.let { runCatching { PawnColor.valueOf(it) }.getOrNull() } }
            ?: listOf(PawnColor.ROSU, PawnColor.ALBASTRU, PawnColor.GALBEN, PawnColor.VERDE)).toTypedArray())
    }
    val isBot = remember { mutableStateListOf(*(saved?.bots ?: listOf(false, false, false, false)).toTypedArray()) }

    val active = (0 until count)
    val taken = active.mapNotNull { colors[it] }.toSet()
    val valid = active.all { names[it].isNotBlank() && colors[it] != null } &&
        taken.size == count && active.any { !isBot[it] }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("← Înapoi") }
            Spacer(Modifier.width(12.dp))
            Text("Jucători", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            (2..4).forEach { n ->
                if (n == count) Button(onClick = { count = n }) { Text("$n") }
                else OutlinedButton(onClick = { count = n }) { Text("$n") }
                Spacer(Modifier.height(0.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        val moneyCap = maxCustomStartingMoney(count)
        if (startMoney != null && startMoney!! > moneyCap) startMoney = null
        Text("Bani de start (fiecare jucător):", style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            val options = listOf<Long?>(null) + (STARTING_MONEY_STEP..moneyCap step STARTING_MONEY_STEP).toList()
            options.forEach { amount ->
                val label = if (amount == null) "Maxim" else "${amount / 1000}k"
                if (amount == startMoney) Button(onClick = { startMoney = amount }) { Text(label) }
                else OutlinedButton(onClick = { startMoney = amount }) { Text(label) }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Durata partidei:", style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf<Int?>(null, 30, 60, 90).forEach { minutes ->
                val label = if (minutes == null) "Fără limită" else "$minutes min"
                if (minutes == timeMinutes) Button(onClick = { timeMinutes = minutes }) { Text(label) }
                else OutlinedButton(onClick = { timeMinutes = minutes }) { Text(label) }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Zaruri:", style = MaterialTheme.typography.bodyLarge)
        Row {
            listOf(1 to "1 zar", 2 to "2 zaruri").forEach { (n, label) ->
                if (n == dice) Button(onClick = { dice = n }) { Text(label) }
                else OutlinedButton(onClick = { dice = n }) { Text(label) }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Tabla:", style = MaterialTheme.typography.bodyLarge)
        Row {
            listOf("old" to "Tablă veche", "new" to "Tablă nouă").forEach { (key, label) ->
                if (key == layout) Button(onClick = { layout = key }) { Text(label) }
                else OutlinedButton(onClick = { layout = key }) { Text(label) }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        active.forEach { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Locul ${i + 1}: ")
                OutlinedButton(onClick = { isBot[i] = !isBot[i] }) {
                    Text(if (isBot[i]) "ROBOT" else "OM")
                }
            }
            OutlinedTextField(
                value = names[i],
                onValueChange = { names[i] = it },
                label = { Text("Nume") },
                modifier = Modifier.fillMaxWidth(),
            )
            ColorPalette(
                selected = colors[i],
                taken = emptySet(),
                onSelect = { chosen ->
                    val other = active.firstOrNull { it != i && colors[it] == chosen }
                    if (other != null) colors[other] = colors[i]
                    colors[i] = chosen
                },
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                SetupMemory.save(
                    context,
                    SetupMemory.Config(
                        count = count,
                        names = names.toList(),
                        colors = colors.map { it?.name },
                        bots = isBot.toList(),
                        money = startMoney,
                        timeMinutes = timeMinutes,
                        dice = dice,
                        layout = layout,
                    ),
                )
                onStart(active.map { PlayerSetup(names[it].trim(), colors[it]!!, isBot[it]) }, startMoney, timeMinutes, dice, layout)
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Începe jocul") }
    }
}

/**
 * Ecranul de joc.
 *
 * Layout: la landscape tabla e în stânga, portofoliul în dreapta; la portrait totul pe coloană.
 * Pionii sunt dimensionați ca părintele căsuței (cota ~0.045 boardHeight * 1.18), deci numele
 * jucătorului se citește de pe tablă; butoanele de zoom și „pe tot ecranul" sunt accesibile mereu.
 */
@Composable
fun GameScreen(vm: GameViewModel, catalog: GameRepository.Catalog, onExit: () -> Unit) {
    val game = vm.game ?: return
    // Online: butoanele sunt active doar pe telefonul jucătorului al cărui rând este
    val myId = vm.myPlayerId
    val online = vm.isOnline
    val myTurn = !online || vm.currentPlayer?.id == myId
    val busy = vm.activeDialog != null || vm.winnerName != null || vm.turnAwaitingEnd ||
        vm.currentPlayer?.isBot == true || !myTurn
    var pawnsBusy by remember { mutableStateOf(false) }   // un pion se mută: dialogurile, roboții și tura așteaptă
    // Robotul acționează după o întârziere de o secundă; online doar gazda rulează roboții
    LaunchedEffect(vm.game, vm.activeDialog, vm.turnAwaitingEnd, vm.currentIndex, vm.winnerName, pawnsBusy) {
        if (!pawnsBusy && !vm.isGuest && vm.winnerName == null && vm.currentPlayer?.isBot == true) {
            // ca în web: cartonașul tras de robot stă 3.5 s, bannerul de staționare 2.5 s, altfel 1 s
            delay(
                when {
                    vm.activeDialog is TurnDialog.CardPending -> 3500L
                    vm.skipBanner != null -> 2500L
                    else -> 1000L
                },
            )
            vm.botNextAction()
        }
    }
    // Tura se încheie singură după ce acțiunea căsuței s-a terminat (zar + cumpărare/refuz, plată sau cartonaș).
    // Roboții își încheie tura singuri; online, încheierea o cere telefonul jucătorului la rând.
    LaunchedEffect(vm.turnAwaitingEnd, vm.activeDialog, vm.currentIndex, vm.winnerName, vm.skipBanner, pawnsBusy) {
        val p = vm.currentPlayer
        if (!pawnsBusy && vm.turnAwaitingEnd && vm.activeDialog == null && vm.winnerName == null &&
            p != null && !p.isBot && (!vm.isOnline || p.id == vm.myPlayerId)
        ) {
            delay(if (vm.skipBanner != null) 2200L else 900L)   // timp să se vadă rezultatul / bannerul
            vm.endTurn()
        }
    }
    // Ceasul partidei (local): se actualizează în fiecare secundă și încheie jocul când expiră timpul
    val saveContext = LocalContext.current
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.deadlineMs, vm.winnerName) {
        while (vm.deadlineMs != null && vm.winnerName == null) {
            clockNow = System.currentTimeMillis()
            if (!vm.isGuest && !vm.isOnline && (vm.timeLeftMs() ?: 1L) <= 0L) {
                vm.timeUp()
                break
            }
            delay(1000)
        }
    }
    // Partida locală se salvează la fiecare schimbare; la final salvarea se șterge
    LaunchedEffect(Unit) {
        snapshotFlow { vm.snapshot() }.collect { s ->
            if (s == null || vm.isOnline) return@collect
            if (s.winnerName != null) SaveStore.clear(saveContext)
            else SaveStore.save(saveContext, s, vm.timeLeftMs())
        }
    }
    val current = vm.currentPlayer
    var viewedId by remember { mutableStateOf<String?>(null) }
    var zoomPreset by remember { mutableFloatStateOf(1f) }
    var zoomNonce by remember { mutableIntStateOf(0) }
    val me = game.players.firstOrNull { it.id == myId }
    val viewed = game.players.firstOrNull { it.id == viewedId } ?: me ?: current ?: game.players.first()
    // Online, dialogurile se văd doar pe telefonul jucătorului vizat (celălalt așteaptă)
    val dialog = vm.activeDialog?.takeIf { d ->
        !pawnsBusy && (!online || when (d) {
            is TurnDialog.BuyDecisionPending -> d.offer.playerId
            is TurnDialog.BankFeePending -> d.offer.playerId
            is TurnDialog.RentDuePending -> d.due.payerId
            is TurnDialog.DebtPending -> d.debt.debtorId
            else -> current?.id
        } == myId)
    }
    // Butonul „înapoi” al telefonului: în joc te întoarce în meniul principal.
    // Numai în momentele care cer decizia jucătorului (dialoguri obligatorii) rămâne fără efect.
    val needsDecision = dialog is TurnDialog.BuyDecisionPending ||
        dialog is TurnDialog.BankFeePending ||
        dialog is TurnDialog.RentDuePending ||
        dialog is TurnDialog.DebtPending ||
        dialog is TurnDialog.LiquidationHandover ||
        dialog is TurnDialog.HandoverPending
    BackHandler { if (!needsDecision) onExit() }
    val propertyTiles = catalog.board.filterIsInstance<Tile.Property>()
        .associate { it.propertyId to it.index }
    // Panoul e detaliat (anexe, chirie) doar pentru jucătorul la rând; adversarii văd doar titlurile
    // Online: fiecare vede detaliile anexelor doar pentru propriile proprietăți
    val detailed = if (online) viewed.id == myId else current != null && viewed.id == current.id
    val sellMode = detailed && dialog is TurnDialog.DebtPending && dialog.debt.debtorId == viewed.id
    val canBuyAnnexFor = if (dialog == null && vm.turnAwaitingEnd && myTurn && current?.id == viewed.id) {
        vm.landedPropertyId(viewed.id)?.takeIf { viewed.ownedProperties.containsKey(it) }
    } else {
        null
    }

    // Tabla pe tot ecranul; zarul stă pe tablă; datele jucătorilor sunt într-un panou retractabil sus
    var hudOpen by remember { mutableStateOf(false) }
    var dieBusy by remember { mutableStateOf(false) }   // zarul se rostogolește: pionii așteaptă să se oprească
    var stretchBoard by remember { mutableStateOf(true) }   // implicit: tabla umple tot ecranul
    var tiltBoard by remember { mutableStateOf(true) }      // tabla înclinată în perspectivă (efect 3D)
    // sunetul: setarea salvată, și butonul din meniu care o schimbă
    val soundContext = LocalContext.current
    remember { GameSound.init(soundContext) }
    var soundOn by remember { mutableStateOf(GameSound.enabled) }
    var hudTouches by remember { mutableIntStateOf(0) }
    // Căsuța pe care s-a aterizat: primește un inel pulsatoriu și un card mare cu nume + iconiță
    // Fișa acțiunii deschise (cartonașul original al unei firme)
    var sheetProperty by remember { mutableStateOf<String?>(null) }
    var landedTile by remember { mutableStateOf<Int?>(null) }
    var cardVisible by remember { mutableStateOf(false) }
    var cardNonce by remember { mutableIntStateOf(0) }
    // căsuța unde urmează să ajungă pionul pulsează din clipa în care starea primește mutarea, ca în web
    LaunchedEffect(vm.currentPlayer?.position, pawnsBusy) {
        if (pawnsBusy) vm.currentPlayer?.let { landedTile = it.position }
    }
    LaunchedEffect(pawnsBusy) {
        if (!pawnsBusy) {
            vm.currentPlayer?.let {
                landedTile = it.position
                cardVisible = true
                cardNonce++
            }
        }
    }
    LaunchedEffect(cardNonce) {
        if (cardNonce > 0) {
            delay(3800)
            cardVisible = false   // căsuța rămâne în memorie, ca animația de ieșire să aibă conținut
        }
    }
    LaunchedEffect(hudOpen, hudTouches) {
        if (hudOpen) {
            delay(4000)     // se închide singur după 4 secunde de inactivitate
            hudOpen = false
        }
    }
    // Zarul se animă doar la o aruncare reală (nu la fiecare mesaj din istoric)
    val dieKey = vm.rollNonce
    // al doilea zar atinge primul: amândouă pornesc aceeași aruncare
    var dieTrigger by remember { mutableIntStateOf(0) }
    // tabla aleasă în configurare: pozițiile, pachetele și săgeata de start
    val boardLayout = catalog.layouts[vm.boardLayout]
        ?: GameRepository.Layout(catalog.positions, catalog.decks, catalog.startMarker)
    val boardIsNew = vm.boardLayout == "new"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenH = maxHeight
        val boardRatio = if (boardIsNew) BOARD_ASPECT_NEW else BOARD_ASPECT_OLD
        val boardModifier = when {
            stretchBoard -> Modifier.fillMaxSize()
            maxWidth / maxHeight > boardRatio -> Modifier.fillMaxHeight()
            else -> Modifier.fillMaxWidth()
        }
        // Banii plătiți de jucătorul uman: zborul spre adversarul care a câștigat, sau spre Bancă
        val cashOwner = viewed.takeIf { !it.isBot } ?: game.players.firstOrNull { !it.isBot }
        val moneySeen = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>() }
        var flight by remember { mutableStateOf<MoneyFlight?>(null) }
        var flightCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(game.players) {
            val before = moneySeen.toMap()
            game.players.forEach { moneySeen[it.id] = it.money }
            val owner = cashOwner ?: return@LaunchedEffect
            val prev = before[owner.id] ?: return@LaunchedEffect
            val paid = prev - owner.money
            if (paid <= 0L) return@LaunchedEffect
            // adversarul care a primit bani în aceeași mișcare; dacă nu există, plata a mers la Bancă
            val receiver = game.players.firstOrNull { p -> p.id != owner.id && (before[p.id] ?: p.money) < p.money }
            val target = receiver?.let { catalog.positions.getOrNull(it.position) }
            flightCount++
            flight = MoneyFlight(
                id = flightCount,
                amount = paid,
                toBank = target == null,
                toFx = target?.first ?: 0.5f,
                toFy = target?.second ?: -0.2f,
            )
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BoardView(
                players = game.players,
                positions = boardLayout.positions,
                propertyTiles = propertyTiles,
                modifier = boardModifier,
                decks = boardLayout.decks,
                boardImage = if (boardIsNew) R.drawable.tabla_noua else R.drawable.tabla_privatizare,
                boardAspect = if (boardIsNew) BOARD_ASPECT_NEW else BOARD_ASPECT_OLD,
                drawKind = (dialog as? TurnDialog.DrawPending)?.kind?.lowercase().takeIf { myTurn && current?.isBot != true },
                onDeckTap = { vm.drawCard() },
                dieBusy = { dieBusy },
                onPawnsBusy = { pawnsBusy = it },
                stretch = stretchBoard,
                presetZoom = zoomPreset,
                presetNonce = zoomNonce,
                focusIndex = current?.position,
                activePlayerId = current?.id,
                highlightTile = landedTile,
                highlightColor = Color(current?.color?.hex ?: 0xFFFFD54F),
                startMarker = boardLayout.startMarker,
                tilt = tiltBoard,
                onTileTap = { idx ->
                    val prop = catalog.board.getOrNull(idx) as? Tile.Property
                    if (prop != null && dialog == null && vm.winnerName == null) sheetProperty = prop.propertyId
                },
                overlay = { bw, bh ->
                    // banii fizici ai jucătorului uman afișat; desenați sub zar, ca zarul să primească atingerile
                    cashOwner?.let { owner ->
                        CashStacks(owner.money, bw, bh, flight)
                    }
                    // zarul în zona liberă din centrul careului
                    val twoDice = vm.diceCount == 2 && !vm.isGuest
                    BoardDie(
                        enabled = !busy,
                        // primul zar: cu două zaruri, celălalt se aruncă odată cu el (suma e pasul)
                        onRolled = { first -> if (twoDice) vm.rollDice(listOf(first, (1..6).random())) else vm.roll(first) },
                        remote = vm.isGuest,   // doar oaspetele așteaptă zarul oficial; gazda aruncă local, aleatoriu
                        resultKey = dieKey,
                        resultValue = vm.lastDice.firstOrNull(),
                        onRollingChange = { dieBusy = it },
                        dieSize = 96.dp,
                        triggerNonce = dieTrigger,
                        badgeText = if (twoDice && vm.lastDice.size == 2) vm.lastDice.sum().toString() else null,
                        modifier = Modifier.offset(x = bw * 0.63f - 48.dp, y = bh * 0.34f - 48.dp),
                    )
                    if (vm.diceCount == 2) {
                        // al doilea zar: arată valoarea lui; atingerea lui aruncă amândouă
                        BoardDie(
                            enabled = !busy,
                            onRolled = {},
                            resultKey = dieKey,
                            resultValue = vm.lastDice.getOrNull(1),
                            dieSize = 96.dp,
                            onTap = { dieTrigger++ },
                            modifier = Modifier.offset(x = bw * (0.63f + 0.075f) - 48.dp, y = bh * 0.34f - 48.dp),
                        )
                    }
                },
            )
            // sumele +/− de pe tablă, 3 secunde, ca în web
            MoneyFloats(game.players, modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
        }

        // Cardul căsuței pe care s-a aterizat: nume mare, iconiță și situația proprietății
        AnimatedVisibility(
            visible = cardVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            val t = landedTile
            val tile = t?.let { catalog.board.getOrNull(it) }
            if (tile != null) {
                val propId = (tile as? Tile.Property)?.propertyId
                val prop = propId?.let { catalog.actions[it] }
                val owner = propId?.let { id -> game.players.firstOrNull { it.ownedProperties.containsKey(id) } }
                val level = propId?.let { owner?.ownedProperties?.get(it) } ?: 0
                TileInfoCard(
                    tile = tile,
                    property = prop,
                    owner = owner,
                    level = level,
                    modifier = Modifier.widthIn(max = 340.dp),
                    onOpen = propId?.let { id -> { sheetProperty = id } },
                )
            }
        }

        // Atingere pe tablă, în afara panoului: îl închide
        if (hudOpen) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { hudOpen = false },
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.62f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density * 0.8f, fontScale = baseDensity.fontScale),
            ) {
                // Banda restrânsă: tura curentă + banii mei + sertarul
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                        .horizontalScroll(rememberScrollState())
                        .clickable { hudOpen = !hudOpen }
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).background(Color(current?.color?.hex ?: 0xFF888888), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "LA RÂND: ${current?.name ?: "-"}",
                        color = Color.White, fontWeight = FontWeight.Bold,
                    )
                    // banii tuturor jucătorilor, fiecare cu culoarea lui
                    game.players.forEach { pl ->
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(10.dp).background(Color(pl.color.hex), CircleShape))
                        Spacer(Modifier.width(4.dp))
                        AnimatedMoney(pl.money, fontSize = 13.sp)
                    }
                    vm.deadlineMs?.let { deadline ->
                        val leftSec = ((deadline - clockNow).coerceAtLeast(0L)) / 1000
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "⏱ %02d:%02d".format(leftSec / 60, leftSec % 60),
                            color = if (leftSec < 60) Color(0xFFFF6B6B) else Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        if (hudOpen) "▲ ÎNCHIDE" else "▼ JUCĂTORI",
                        color = Color(0xFFF0D20E), fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false,
                    )
                }
                vm.skipBanner?.let { msg ->
                    Text(
                        msg, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp)).padding(6.dp),
                    )
                }
                if (online && !myTurn) {
                    Text(
                        "Așteaptă: ${current?.name ?: ""} își joacă tura…", color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }

                // Sertarul: peste tablă, cu toate datele
                AnimatedVisibility(visible = hudOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = screenH - 60.dp)
                            .background(Color(0xF5221B1A), RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        hudTouches++          // orice atingere în panou resetează cronometrul
                                    }
                                }
                            }
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        game.players.forEach { p ->
                            Text(
                                "${p.name}: ${p.money} ECU, ${p.ownedProperties.size} firme",
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Zoom: ", color = Color.White)
                            OutlinedButton(onClick = { zoomPreset = 1f; zoomNonce++ }) { Text("1x") }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { zoomPreset = 2f; zoomNonce++ }) { Text("2x") }
                            Spacer(Modifier.width(8.dp))
                            Text("sau ciupește pe tablă", fontSize = 11.sp, color = Color.White)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { stretchBoard = !stretchBoard }) {
                                Text(if (stretchBoard) "Tablă: pe tot ecranul" else "Tablă: proporțională")
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { tiltBoard = !tiltBoard }) {
                                Text(if (tiltBoard) "3D: pornit" else "3D: oprit")
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { soundOn = GameSound.toggle(soundContext) }) {
                                Text(if (soundOn) "Sunet: pornit" else "Sunet: oprit")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row {
                            game.players.forEach { p ->
                                OutlinedButton(onClick = { viewedId = p.id }) { Text(p.name) }
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        PlayerPortfolioPanel(
                            player = viewed,
                            actions = catalog.actions,
                            detailed = detailed,
                            canBuyAnnexFor = canBuyAnnexFor,
                            onBuyAnnex = { vm.buyNextAnnex(it) },
                            sellMode = sellMode,
                            onSell = { vm.settleDebt(listOf(it)) },
                            onOpenCard = { id ->
                                hudOpen = false
                                sheetProperty = id
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Istoric:", color = Color.White)
                        game.history.takeLast(6).forEach { Text("• $it", color = Color.White, fontSize = 12.sp) }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onExit) { Text("Ieșire în meniu") }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is TurnDialog.BuyDecisionPending -> {
            val buyer = game.players.firstOrNull { it.id == d.offer.playerId }
            if (buyer != null) {
                BuyDecisionDialog(
                    option = d.offer.option,
                    property = d.property,
                    buyer = buyer,
                    onBuy = { vm.acceptOffer() },
                    onRefuse = { vm.refuseOffer() },
                )
            }
        }
        is TurnDialog.BankFeePending -> BankFeeDialog(
            property = d.property,
            taxAmount = d.taxAmount,
            onOk = { vm.acknowledgeBankFee() },
        )
        is TurnDialog.RentDuePending -> RentDueDialog(
            property = d.property,
            owner = d.owner,
            rentAmount = d.rentAmount,
            onPay = { vm.payRent() },
        )
        is TurnDialog.LiquidationHandover -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Predați dispozitivul") },
            text = {
                Text(
                    "Atenție: ${d.debtorName} trebuie să își lichideze din active! " +
                        "Predați dispozitivul lui ${d.debtorName} — Apasă când ești gata!",
                )
            },
            confirmButton = { Button(onClick = { vm.acknowledgeLiquidation() }) { Text("SUNT GATA") } },
        )
        is TurnDialog.HandoverPending -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Predați telefonul") },
            text = { Text("Urmează la rând: ${d.playerName} — Apasă când ești gata!") },
            confirmButton = { Button(onClick = { vm.acknowledgeHandover() }) { Text("SUNT GATA") } },
        )
        is TurnDialog.DrawPending -> Unit   // fără fereastră: pachetul de pe tablă pulsează și scrie TRAGE
        is TurnDialog.CardPending -> CardDrawDialog(
            kind = d.card.kind,
            text = d.card.text,
            outcome = d.card.outcome,
            amount = d.card.amount,
            destination = d.card.destination,
            artPath = d.card.cardId?.let { catalog.cardArt[it] },
            onConfirm = { vm.dismissCard() },
        )
        is TurnDialog.DebtPending -> {
            val debtor = game.players.firstOrNull { it.id == d.debt.debtorId }
            if (debtor != null) {
                LiquidationDialog(
                    debt = d.debt,
                    debtor = debtor,
                    sellable = vm.sellableFor(debtor.id),
                    actions = catalog.actions,
                    onSell = { vm.settleDebt(listOf(it)) },
                    onBankrupt = { vm.settleDebt(emptyList()) },
                )
            }
        }
        null -> Unit
    }

    // Fișa acțiunii: cartonașul original + anexele lui
    sheetProperty?.let { id ->
        val prop = catalog.actions[id]
        if (prop != null) {
            val owner = game.players.firstOrNull { it.ownedProperties.containsKey(id) }
            val level = owner?.ownedProperties?.get(id) ?: 0
            ActionCardSheet(
                property = prop,
                owner = owner,
                level = level,
                canBuyAnnex = canBuyAnnexFor == id,
                onBuyAnnex = { vm.buyNextAnnex(id) },
                onClose = { sheetProperty = null },
            )
        }
    }
    // Când apare un dialog de joc, fișa se închide ca să nu se suprapună
    LaunchedEffect(dialog) { if (dialog != null) sheetProperty = null }
    // sunetele ferestrelor de joc, ca în web: cartonaș, cumpărare, datorie
    LaunchedEffect(dialog) {
        when (dialog) {
            is TurnDialog.CardPending -> GameSound.card()
            is TurnDialog.BuyDecisionPending -> GameSound.coin()
            is TurnDialog.DebtPending -> GameSound.alert()
            else -> Unit
        }
    }

    var winVideoDone by remember { mutableStateOf(false) }
    // la fereastra „Joc terminat” se aude cumpărarea, ca în web
    LaunchedEffect(vm.winnerName, winVideoDone) {
        if (vm.winnerName != null && winVideoDone) GameSound.buy()
    }
    vm.winnerName?.let { winner ->
        if (!winVideoDone) {
            // Clipul de victorie pe tot ecranul; apoi apare fereastra cu câștigătorul.
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                VideoScreen(rawResId = R.raw.win_video) { winVideoDone = true }
            }
        } else AlertDialog(
            onDismissRequest = {},
            title = { Text("Joc terminat") },
            text = {
                Column {
                    Text("Câștigător: $winner", fontWeight = FontWeight.Bold)
                    if (vm.finalRanking.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Clasament (bani + acțiuni la 80%):")
                        vm.finalRanking.forEachIndexed { i, line -> Text("${i + 1}. $line") }
                    }
                }
            },
            confirmButton = { Button(onClick = onExit) { Text("Meniu") } },
        )
    }
}

/** Bannerul de tură: numele jucătorului la rând, pe fundalul culorii pionului său. */
@Composable
fun TurnBanner(player: com.example.privatizare.data.model.Player?) {
    val bg = player?.let { Color(it.color.hex) } ?: Color.Gray
    val fg = if (player?.color == PawnColor.ALB_ARGINTIU) Color.Black else Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(6.dp))
            .padding(10.dp),
    ) {
        Text(
            "LA RÂND: ${player?.name ?: "-"}",
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}
