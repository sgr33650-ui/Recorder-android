# Enregistreur d'appels (Android)

Appli personnelle qui enregistre automatiquement les appels entrants et sortants, testée pour les téléphones Xiaomi / Poco (HyperOS).

## Télécharger l'APK

Chaque modification poussée sur `main` lance une compilation (onglet **Actions**). L'APK est ensuite publié dans **Releases → Dernière version → EnregistreurAppels.apk**.

## Installation sur le Poco

1. Télécharge `EnregistreurAppels.apk` depuis le téléphone et ouvre-le. Autorise « Installer des applis inconnues » pour ton navigateur si on te le demande.
2. Ouvre l'appli et suis les 3 boutons dans l'ordre :
   1. **Autoriser les permissions** (micro, téléphone, journal d'appels, notifications).
   2. **Activer le service d'accessibilité** : Applis téléchargées → Enregistreur d'appels → Activer.
      Sur Android 13+, si l'option est grisée : Paramètres → Applis → Enregistreur d'appels → ⋮ → **Autoriser les paramètres restreints**, puis recommence.
   3. **Désactiver l'optimisation batterie**.
3. Réglages spécifiques Xiaomi/Poco (Paramètres → Applis → Enregistreur d'appels) :
   - **Démarrage automatique** : activé
   - **Économiseur de batterie** : « Aucune restriction »
   - Dans le menu des applis récentes, appui long sur l'appli → 🔒 Verrouiller.

## Limites à connaître

- Depuis Android 10, Android bloque l'enregistrement direct du son de l'appel par les applis tierces. Cette appli passe par le micro avec un service d'accessibilité (même méthode que Cube ACR). **Ta voix est bien captée ; celle de ton interlocuteur peut être faible**, selon le modèle.
- Si l'interlocuteur est trop faible : active « Mettre le haut-parleur pendant l'appel », ou change la **Source audio** (le bouton fait défiler les choix) et refais un essai.
- Sur les appels sortants, le numéro n'est pas toujours disponible (fichier nommé « inconnu »).
- Les fichiers `.m4a` sont rangés dans `Android/data/fr.perso.enregistreur/files/Enregistrements/`. Ils sont supprimés si tu désinstalles l'appli : partage ceux à garder.

## Légalité

En France, enregistrer une conversation privée à l'insu de l'autre personne, puis l'utiliser ou la diffuser, peut être sanctionné (art. 226-1 du Code pénal). Préviens tes interlocuteurs.
