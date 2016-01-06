package com.xebialabs.gradle.plugins.dependency.supplier

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class FileSupplier extends ConfigSupplier {
    private File file
    private Config config

    FileSupplier(File file) {
        this.file = file
    }

    Config getConfig() {
        if (!config) {
            config = ConfigFactory.parseFile(file)
        }
        return config
    }
}
