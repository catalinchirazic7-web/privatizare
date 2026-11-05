package com.example.privatizare.logic

import kotlin.random.Random

/** Un singur zar, uniform între 1 și 6. Sursa unică de zaruri a aplicației (om, gazdă online, robot). */
fun rollDie(random: Random = Random.Default): Int = random.nextInt(1, 7)

/** Șansa ca robotul CatalinRobot să arunce de două ori și să ia fața mai mare (la fel ca în web). */
const val CATALIN_LUCK = 0.25

/**
 * Zarul robotului. Cu probabilitatea [luck] aruncă de două ori și păstrează fața mai mare;
 * altfel o aruncare normală. Cu luck = 0 e identic cu [rollDie].
 */
fun rollBotDie(random: Random = Random.Default, luck: Double = 0.0): Int {
    val first = rollDie(random)
    if (luck <= 0.0 || random.nextDouble() >= luck) return first
    return maxOf(first, rollDie(random))
}
