ARCHS = armv7
TARGET = iphone:clang:9.2:9.0
INSTALL_TARGET_PROCESSES = SpringBoard

include $(THEOS)/makefiles/common.mk

APPLICATION_NAME = ShockTV
ShockTV_FILES = main.m AppDelegate.m ShockViewController.m ShockDetailViewController.m ShockStreamChecker.m TelaresSubtitles.m
ShockTV_FRAMEWORKS = UIKit Foundation AVFoundation AVKit CoreMedia ImageIO
ShockTV_CFLAGS = -fobjc-arc -O2
ShockTV_RESOURCE_DIRS = Resources

include $(THEOS_MAKE_PATH)/application.mk

