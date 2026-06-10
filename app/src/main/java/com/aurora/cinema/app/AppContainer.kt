package com.aurora.cinema.app

import android.content.Context

interface AppContainer {
    val applicationContext: Context
}

class DefaultAppContainer(
    override val applicationContext: Context,
) : AppContainer
