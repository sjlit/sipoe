# Sipoe Android 构建 / 签名 / 安装
#
# 先看帮助:  make
# 常用:      make verify   (debug + release + lint)
#            make release  (自动用 keystore.properties 签名)
#            make dist     (输出到 dist/,文件名带版本号)

SHELL := /bin/bash
.NOTPARALLEL:

GRADLEW := ./gradlew

VERSION      := $(shell sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)
VERSION_CODE := $(shell sed -n 's/.*versionCode *= *\([0-9]*\).*/\1/p' app/build.gradle.kts | head -1)

SDK_DIR       := $(shell sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)
ANDROID_HOME  ?= $(if $(SDK_DIR),$(SDK_DIR),$(HOME)/Android/Sdk)
export ANDROID_HOME
export ANDROID_SDK_ROOT := $(ANDROID_HOME)
JAVA_HOME     ?= /usr/lib/jvm/java-17-openjdk-amd64
export JAVA_HOME

BUILD_TOOLS := $(shell ls $(ANDROID_HOME)/build-tools 2>/dev/null | sort -V | tail -1)
APKSIGNER   := $(ANDROID_HOME)/build-tools/$(BUILD_TOOLS)/apksigner
ADB         := $(ANDROID_HOME)/platform-tools/adb

DEBUG_APK   := app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK := app/build/outputs/apk/release/app-release.apk
DIST_DIR    := dist

.PHONY: help debug release apk lint verify dist sign verify-sign certs keystore \
        version install install-release devices clean distclean

help: ## 显示帮助
	@echo "Sipoe v$(VERSION) ($(VERSION_CODE))  构建工具 $(BUILD_TOOLS)"
	@echo ""
	@echo "可用目标:"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z0-9_-]+:.*?## / {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "签名配置: $(if $(wildcard keystore.properties),keystore.properties 已找到,$(if $(wildcard keystore/sipoe-release.jks),只有 .jks 缺少 keystore.properties,请执行 make keystore,未配置,请执行 make keystore))"
	@echo "提示: WSL2 下 adb 可能挂起,建议用 Windows 侧 adb 安装 APK。"

debug: ## 构建 debug APK
	@$(GRADLEW) :app:assembleDebug
	@ls -lh $(DEBUG_APK)

release: ## 构建 release APK(用 keystore.properties 自动签名)
	@$(GRADLEW) :app:assembleRelease
	@ls -lh $(RELEASE_APK)

apk: debug release ## 同时构建 debug 与 release

lint: ## 运行 Android Lint
	@$(GRADLEW) :app:lintDebug
	@tail -2 app/build/reports/lint-results-debug.txt

verify: ## 构建 debug + release 并跑 lint(提交前检查)
	@$(GRADLEW) :app:assembleDebug :app:assembleRelease :app:lintDebug
	@tail -2 app/build/reports/lint-results-debug.txt

dist: apk ## 构建并把两个 APK 复制到 dist/(文件名含版本号)
	@mkdir -p $(DIST_DIR)
	@cp $(DEBUG_APK) $(DIST_DIR)/sipoe-$(VERSION)-debug.apk
	@cp $(RELEASE_APK) $(DIST_DIR)/sipoe-$(VERSION)-release.apk
	@echo ""
	@ls -lh $(DIST_DIR)/sipoe-$(VERSION)-*.apk

sign: ## 签名任意 APK: make sign APK=<路径> [OUT=<输出路径>]
	@test -n "$(APK)" || { echo "用法: make sign APK=<apk 路径> [OUT=<输出路径>]"; exit 1; }
	@test -f "$(APK)" || { echo "找不到 $(APK)"; exit 1; }
	@test -f keystore.properties || { echo "缺少 keystore.properties,请先执行 make keystore"; exit 1; }
	@set -e; \
	store=$$(sed -n 's/^storeFile=//p' keystore.properties); \
	storePass=$$(sed -n 's/^storePassword=//p' keystore.properties); \
	keyAlias=$$(sed -n 's/^keyAlias=//p' keystore.properties); \
	keyPass=$$(sed -n 's/^keyPassword=//p' keystore.properties); \
	out="$(if $(OUT),$(OUT),$(basename $(APK))-signed.apk)"; \
	$(APKSIGNER) sign --ks "$$store" --ks-key-alias "$$keyAlias" \
		--ks-pass "pass:$$storePass" --key-pass "pass:$$keyPass" \
		--out "$$out" "$(APK)"; \
	echo "已签名: $$out"

verify-sign: ## 校验 release APK 签名
	@test -f $(RELEASE_APK) || { echo "请先 make release"; exit 1; }
	@$(APKSIGNER) verify --print-certs $(RELEASE_APK)

certs: ## 打印 release 签名证书 DN 与 SHA-256 指纹
	@test -f $(RELEASE_APK) || { echo "请先 make release"; exit 1; }
	@$(APKSIGNER) verify --print-certs $(RELEASE_APK) | grep -E "DN|SHA-256"

keystore: ## 生成 release 签名密钥库(已存在则跳过)
	@if [ -f keystore.properties ] || [ -f keystore/sipoe-release.jks ]; then \
		echo "已存在 keystore.properties 或 keystore/sipoe-release.jks,跳过生成"; \
	else \
		mkdir -p keystore; \
		pw=$$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24); \
		keytool -genkeypair -keystore keystore/sipoe-release.jks -alias sipoe \
			-keyalg RSA -keysize 2048 -validity 10000 \
			-storepass "$$pw" -keypass "$$pw" \
			-dname "CN=Sipoe, OU=Mobile, O=Sipoe, C=CN" >/dev/null 2>&1; \
		printf 'storeFile=keystore/sipoe-release.jks\nstorePassword=%s\nkeyAlias=sipoe\nkeyPassword=%s\n' "$$pw" "$$pw" > keystore.properties; \
		chmod 600 keystore.properties; \
		echo "已生成 keystore/sipoe-release.jks 与 keystore.properties"; \
		echo "密码: $$pw"; \
		echo "请立即备份密钥库与密码!丢失后无法再更新应用。"; \
	fi

version: ## 打印版本号
	@echo "versionName=$(VERSION)  versionCode=$(VERSION_CODE)"

install: debug ## 安装 debug APK 到设备
	@$(ADB) install -r $(DEBUG_APK)

install-release: release ## 安装 release APK 到设备(签名不同,装不上需先卸载 debug 版)
	@$(ADB) install -r $(RELEASE_APK)

devices: ## 列出 adb 设备
	@$(ADB) devices

clean: ## 清理 Gradle 构建产物
	@$(GRADLEW) clean
	@echo "已清理 build 目录"

distclean: clean ## 清理构建产物和 dist/
	@rm -rf $(DIST_DIR)
	@echo "已删除 $(DIST_DIR)/"
