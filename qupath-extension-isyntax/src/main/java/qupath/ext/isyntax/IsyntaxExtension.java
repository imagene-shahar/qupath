/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers
 * %%
 * GPLv3
 * #L%
 */
package qupath.ext.isyntax;

import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.localization.LocalizedResourceManager;
import qupath.fx.prefs.annotations.DirectoryPref;
import qupath.fx.prefs.annotations.PrefCategory;
import qupath.fx.prefs.controlsfx.PropertySheetUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.servers.isyntax.jna.IsyntaxLoader;

import java.io.File;
import java.util.Arrays;

@PrefCategory("category.isyntax")
public class IsyntaxExtension implements QuPathExtension {

    private static final Logger logger = LoggerFactory.getLogger(IsyntaxExtension.class);

    @DirectoryPref("pref.isyntax.path")
    public static final StringProperty isyntaxPathProperty = PathPrefs.createPersistentPreference("isyntax.path", "");

    private final javafx.beans.value.ChangeListener<String> pathListener = this::handleDirectoryChange;

    @Override
    public void installExtension(QuPathGUI qupath) {
        installPreferences(qupath);
        isyntaxPathProperty.addListener(pathListener);
        if (!IsyntaxLoader.tryToLoadQuietly(isyntaxPathProperty.get())) {
            logger.warn("libisyntax not found! Optionally specify the directory containing the library in preferences.");
        } else {
            logger.info("libisyntax loaded successfully");
        }
    }

    private void installPreferences(QuPathGUI qupath) {
        var prefs = PropertySheetUtils.parseAnnotatedItemsWithResources(
                LocalizedResourceManager.createInstance("qupath.ext.isyntax.strings"),
                this);
        qupath.getPreferencePane().getPropertySheet().getItems().addAll(prefs);
    }

    private void handleDirectoryChange(ObservableValue<? extends String> value, String oldValue, String newValue) {
        if (!IsyntaxLoader.isAvailable() && newValue != null) {
            if (isPotentialDirectory(newValue)) {
                try {
                    if (IsyntaxLoader.tryToLoad(newValue)) {
                        logger.info("libisyntax loaded successfully");
                        Dialogs.showInfoNotification("iSyntax", "libisyntax loaded successfully");
                    } else {
                        logger.warn("libisyntax could not be loaded from {}", newValue);
                    }
                } catch (Throwable t) {
                    logger.debug("libisyntax loading failed", t);
                }
            }
        } else if (newValue != null && newValue.isEmpty()) {
            Dialogs.showInfoNotification("iSyntax", "libisyntax directory reset - please restart QuPath");
        } else if (isPotentialDirectory(newValue)) {
            Dialogs.showInfoNotification("iSyntax", "libisyntax directory updated - please restart QuPath");
        }
    }

    private static boolean isPotentialDirectory(String path) {
        if (path == null || path.isEmpty()) return false;
        var file = new File(path);
        if (!file.isDirectory()) return false;
        return Arrays.stream(file.listFiles())
                .filter(File::isFile)
                .map(f -> f.getName().toLowerCase())
                .anyMatch(n -> n.contains("isyntax"));
    }

    @Override public String getName() { return "iSyntax extension"; }
    @Override public String getDescription() { return "Provides support for Philips iSyntax via libisyntax."; }
}