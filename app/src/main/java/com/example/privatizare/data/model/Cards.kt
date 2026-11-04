package com.example.privatizare.data.model

enum class LotoKind { WIN, LOSE, NOTHING }

/** Cartonaș LOTO. WIN = banca plătește jucătorului, LOSE = jucătorul plătește băncii. */
data class LotoCard(
    val id: String,
    val kind: LotoKind,
    val amount: Long,
    val text: String,
)

sealed class CardEffect {
    data class PayBank(val amount: Long) : CardEffect()

    /** Proprietarul obiectivului, dacă e alt jucător; altfel banca. */
    data class PayOwnerOrBank(val amount: Long, val propertyId: String) : CardEffect()
    data class ReceiveBank(val amount: Long) : CardEffect()
    data class ReceiveFromEachPlayer(val amount: Long) : CardEffect()
    data class Move(val steps: Int) : CardEffect()
    data class SkipTurns(val turns: Int) : CardEffect()
}

/** Cine primește banii plătiți de un cartonaș TELEX. */
enum class BeneficiaryType {
    /** Banca (fondul general). */
    BANK,

    /** Proprietarul companiei [TelexCard.targetCompanyId]; dacă nu e privatizată, banca. */
    COMPANY_OWNER,

    /** Un jucător anume (rezervat). */
    TARGET_PLAYER,
}

data class TelexCard(
    val id: String,
    val text: String,
    val effects: List<CardEffect>,
    /** Destinația banilor scrisă pe cartonaș (null = cartonașul nu cere o plată). */
    val beneficiaryType: BeneficiaryType? = null,
    /** Compania (id din actions.json) numită pe cartonaș, pentru [BeneficiaryType.COMPANY_OWNER]. */
    val targetCompanyId: String? = null,
    /**
     * true dacă numele companiei a fost scris cu pixul de jucător pe cartonașul fizic.
     * Regula casei: dacă jucătorul care trage cartonașul deține chiar el compania scrisă cu pixul,
     * nu plătește nimic (nici băncii, nici altui jucător).
     */
    val handwritten: Boolean = false,
)
