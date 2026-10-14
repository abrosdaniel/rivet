package dev.abros.rivet.client;
import net.minecraft.client.gui.screens.Screen;
/** Explicit modal ancestry for features that must keep the underlying screen context. */
interface UiParentScreen { Screen parentScreen(); }
