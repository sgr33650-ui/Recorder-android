package fr.perso.enregistreur

/** Rempli par le service d'accessibilité (version complète uniquement). */
object Screenshotter {
    @Volatile var take: ((String) -> Unit)? = null
}
