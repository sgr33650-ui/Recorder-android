# Appels Rec — enregistreur d'appels Android

Appli personnelle qui enregistre automatiquement les appels entrants et sortants, testée pour les téléphones Xiaomi / Poco (HyperOS).

## Télécharger l'APK

Chaque modification poussée sur `main` lance une compilation (onglet **Actions**). L'APK est ensuite publié dans **Releases → Dernière version → AppelsRec.apk**.

## Installation sur le Poco

1. Télécharge `AppelsRec.apk` depuis le téléphone et ouvre-le. Autorise « Installer des applis inconnues » pour ton navigateur si on te le demande.
2. Ouvre l'appli et suis les 3 boutons dans l'ordre :
   1. **Autoriser les permissions** (micro, téléphone, journal d'appels, notifications).
   2. **Activer le service d'accessibilité** : Applis téléchargées → Enregistreur d'appels → Activer.
      Sur Android 13+, si l'option est grisée : Paramètres → Applis → Enregistreur d'appels → ⋮ → **Autoriser les paramètres restreints**, puis recommence.
   3. **Désactiver l'optimisation batterie**.
3. Réglages spécifiques Xiaomi/Poco (Paramètres → Applis → Enregistreur d'appels) :
   - **Démarrage automatique** : activé
   - **Économiseur de batterie** : « Aucune restriction »
   - Dans le menu des applis récentes, appui long sur l'appli → 🔒 Verrouiller.

## Liste des appels, horodatage et captures

- Chaque appel apparaît dans l'appli avec **date, heure, sens (entrant/sortant), numéro et durée**. 📷 indique qu'une capture existe.
- Une **capture d'écran** de l'écran d'appel est prise automatiquement ~1,5 s après le décroché (Android 11 ou plus).
- Appui long sur un appel : voir la capture, partager audio + capture, supprimer.
- **Exporter la liste** produit un tableau CSV (Date ; Heure ; Sens ; Numéro ; Durée ; fichiers) à ouvrir dans Excel ou Google Sheets.
- **À la fin de chaque appel**, une fenêtre propose « Conserver » ou « Supprimer » (avec aperçu de la capture et bouton Écouter). La suppression demande une confirmation. Retour = conserver. Une notification sert de secours si la fenêtre ne s'ouvre pas : sur Xiaomi/Poco, active **Autres autorisations → Afficher les fenêtres pop-up en arrière-plan**.
- Après une mise à jour de l'appli, si les captures n'arrivent pas, désactive puis réactive le service d'accessibilité.

## Limites à connaître

- Depuis Android 10, Android bloque l'enregistrement direct du son de l'appel par les applis tierces. Cette appli passe par le micro avec un service d'accessibilité (même méthode que Cube ACR). **Ta voix est bien captée ; celle de ton interlocuteur peut être faible**, selon le modèle.
- Si l'interlocuteur est trop faible : active « Mettre le haut-parleur pendant l'appel », ou change la **Source audio** (le bouton fait défiler les choix) et refais un essai.
- Sur les appels sortants, le numéro n'est pas toujours disponible (fichier nommé « inconnu »).
- Les fichiers `.m4a` sont rangés dans `Android/data/fr.perso.recorder/files/Enregistrements/`. Ils sont supprimés si tu désinstalles l'appli : partage ceux à garder.

## Légalité

En France, enregistrer une conversation privée à l'insu de l'autre personne, puis l'utiliser ou la diffuser, peut être sanctionné (art. 226-1 du Code pénal). Préviens tes interlocuteurs.
