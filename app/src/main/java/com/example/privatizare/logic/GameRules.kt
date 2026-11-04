package com.example.privatizare.logic

/** Constante de reguli, în ECU. Valorile urmează regulamentul original, confirmat de utilizator. */
object GameRules {

    /**
     * Poziția de plecare: săgeata START. Nu e o căsuță a traseului — primul pas (1) duce la
     * prima căsuță reală (CONTINENTAL), iar un tur complet înseamnă 37 de pași.
     */
    const val START_POSITION = -1

    /** Bonus la fiecare trecere prin START (după fiecare circuit). */
    const val START_BONUS = 3000L

    /** Căsuța BANCA: banca plătește dobândă jucătorului. */
    const val BANK_INTEREST = 5000L

    /** Căsuța BANCA ACHITĂ 2 000 ECU DIVIDENDE. */
    const val DIVIDEND = 2000L

    /** SALA CONSFĂTUIRILOR: taxă plătită la Bancă. */
    const val SALA_CONSFATUIRI_FEE = 1000L

    /** CASA DE ODIHNĂ: numărul de ture pierdute. */
    const val CASA_ODIHNA_SKIP_TURNS = 2

    /** Banca cumpără înapoi acțiuni la această proporție din valoarea inițială. */
    const val RESALE_RATIO_PERCENT = 80L
}

/** Tiraj fizic complet al bancnotelor, denominare -> număr de bucăți. Totalul este 500 000 ECU. */
val BILL_SUPPLY: Map<Long, Int> = linkedMapOf(50L to 80, 100L to 160, 500L to 160, 1000L to 240, 2000L to 80)

/**
 * Rezultatul distribuției inițiale. [playerMoney] = soldul fiecărui jucător, [bankMoney] = restul din casierie.
 * Suma playerCount * playerMoney + bankMoney este întotdeauna totalul din [BILL_SUPPLY].
 */
data class StartingDistribution(
    val playerCount: Int,
    val billsPerPlayer: Map<Long, Int>,
    val playerMoney: Long,
    val bankBills: Map<Long, Int>,
    val bankMoney: Long,
)

/** Pasul sumelor de start alese de jucători (ECU). */
const val STARTING_MONEY_STEP = 50_000L

/** Cât valorează tot tirajul de bancnote. */
const val TOTAL_SUPPLY = 500_000L

/** Cea mai mare sumă de start aleasă (în pași de [STARTING_MONEY_STEP]) care încape în tiraj pentru [playerCount] jucători. */
fun maxCustomStartingMoney(playerCount: Int): Long =
    (TOTAL_SUPPLY / playerCount / STARTING_MONEY_STEP) * STARTING_MONEY_STEP

/**
 * Împărțire egală la start: fiecare denominare se împarte întreg între jucători (totalBills / numPlayers).
 * Restul (totalBills % numPlayers) rămâne în casieria Băncii.
 */
fun distributeStartingMoney(playerCount: Int): StartingDistribution {
    require(playerCount in 2..4) { "Între 2 și 4 jucători, nu $playerCount" }
    val perPlayer = BILL_SUPPLY.mapValues { (_, total) -> total / playerCount }
    val rest = BILL_SUPPLY.mapValues { (_, total) -> total % playerCount }
    return StartingDistribution(
        playerCount = playerCount,
        billsPerPlayer = perPlayer,
        playerMoney = perPlayer.entries.sumOf { it.key * it.value },
        bankBills = rest,
        bankMoney = rest.entries.sumOf { it.key * it.value },
    )
}

/**
 * Sumă de start aleasă de jucători: fiecare primește exact [moneyPerPlayer] ECU, iar restul tirajului
 * rămâne în casierie. Bancnotele se dau pe rând, câte una pe jucător, de la cea mai mare la cea mai mică
 * care mai încape, ca toți să primească același număr de bucăți când tirajul permite.
 */
fun distributeStartingMoney(playerCount: Int, moneyPerPlayer: Long): StartingDistribution {
    require(playerCount in 2..4) { "Între 2 și 4 jucători, nu $playerCount" }
    require(moneyPerPlayer > 0 && moneyPerPlayer % STARTING_MONEY_STEP == 0L) { "Sumă invalidă: $moneyPerPlayer" }
    require(moneyPerPlayer <= maxCustomStartingMoney(playerCount)) { "Suma $moneyPerPlayer depășește tirajul pentru $playerCount jucători" }

    val bank = BILL_SUPPLY.toMutableMap()
    val owned = List(playerCount) { mutableMapOf<Long, Int>() }
    val remaining = LongArray(playerCount) { moneyPerPlayer }
    var progress = true
    while (remaining.any { it > 0 } && progress) {
        progress = false
        for (i in 0 until playerCount) {
            if (remaining[i] == 0L) continue
            val bill = bank.keys.filter { (bank[it] ?: 0) > 0 && it <= remaining[i] }.maxOrNull() ?: continue
            bank[bill] = bank.getValue(bill) - 1
            owned[i][bill] = (owned[i][bill] ?: 0) + 1
            remaining[i] -= bill
            progress = true
        }
    }
    check(remaining.all { it == 0L }) { "Tirajul nu acoperă $moneyPerPlayer ECU pentru fiecare jucător" }

    val rest = bank.filterValues { it > 0 }
    return StartingDistribution(
        playerCount = playerCount,
        billsPerPlayer = owned[0],
        playerMoney = moneyPerPlayer,
        bankBills = rest,
        bankMoney = rest.entries.sumOf { it.key * it.value },
    )
}
