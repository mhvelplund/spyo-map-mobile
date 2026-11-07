package net.isntall.spyomap

/** Hardware/gesture back: walk the site's history first (hash routes count), then leave the app. */
object BackNavigation {
    fun onBack(canGoBack: Boolean, goBack: () -> Unit, leave: () -> Unit) {
        if (canGoBack) goBack() else leave()
    }
}
