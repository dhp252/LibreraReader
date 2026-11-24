# Where the app's synced folder is. Override with env YGG_ROOT (rclone path) and YGG_PROFILE.
import os
ROOT = os.environ.get('YGG_ROOT', 'koofr:Yggdrasil')
PROF = os.environ.get('YGG_PROFILE', 'profile.Librera')
MODEL = 'Claude (outside the app)'
FEATURES = ['summary', 'book', 'highlights', 'skeleton', 'preq', 'xray', 'updated']
