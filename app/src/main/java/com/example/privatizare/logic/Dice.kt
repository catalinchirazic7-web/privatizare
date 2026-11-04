package com.example.privatizare.logic

import kotlin.random.Random

/** Un singur zar, uniform între 1 și 6. Sursa unică de zaruri a aplicației (om, gazdă online, robot). */
fun rollDie(random: Random = Random.Default): Int = random.nextInt(1, 7)
