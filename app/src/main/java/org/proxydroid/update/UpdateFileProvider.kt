package org.proxydroid.update

import androidx.core.content.FileProvider

/** Explicit subclass avoids FileProvider initialization issues on some OEM builds. */
class UpdateFileProvider : FileProvider()
