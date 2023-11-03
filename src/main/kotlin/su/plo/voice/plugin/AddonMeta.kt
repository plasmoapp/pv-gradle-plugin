package su.plo.voice.plugin

import su.plo.voice.api.addon.AddonDependency
import su.plo.voice.api.addon.AddonLoaderScope

data class AddonMeta(
    val id: String,
    val name: String,
    val loaderScope: AddonLoaderScope,
    val version: String,
    val license: String,
    val authors: List<String>,
    val dependencies: List<AddonDependency>,
    val entryPoint: String
)
