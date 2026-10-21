package com.pescasub.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de navegación sobre la app real en emulador (sin datos previos). No toca la red: solo recorre las
 * pestañas que muestran contenido local y comprueba los estados vacíos (nada de datos inventados).
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeAsksForALocationWhenNoneIsChosen() {
        rule.onNodeWithText("Elige una ubicación").assertIsDisplayed()
    }

    @Test
    fun bottomBarReachesLogbookAndProfileWithHonestEmptyStates() {
        rule.onNodeWithText("Bitácora").performClick()
        rule.onNodeWithText("Aún no hay jornadas").assertIsDisplayed()

        rule.onNodeWithText("Perfil").performClick()
        rule.onNodeWithText("Tus datos").assertIsDisplayed()
        rule.onNodeWithText("Exportar mis datos (JSON)").assertIsDisplayed()
    }

    @Test
    fun newSessionFormOpensFromTheEmptyLogbook() {
        rule.onNodeWithText("Bitácora").performClick()
        // "Nueva jornada" aparece dos veces en la bitácora vacía (botón del estado vacío y botón flotante).
        rule.onAllNodesWithText("Nueva jornada")[0].performClick()
        rule.onNodeWithText("Cuándo y qué").assertIsDisplayed()
    }

    @Test
    fun forecastTabExplainsThereIsNoLocationYet() {
        rule.onNodeWithText("Previsión").performClick()
        rule.onNodeWithText("Elige una ubicación").assertIsDisplayed()
    }
}
