package com.sunuhwi.poe2dbkr

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * 전면 광고 — PoB 빌드 코드를 붙여넣고 '불러오기'를 누를 때 한 번 보여 준다.
 *
 * 광고 때문에 빌드 불러오기가 막히면 안 되므로,
 * - 광고가 미리 받아져 있으면 먼저 보여 주고 닫힌 뒤에 빌드를 연다.
 * - 아직 받지 못했거나(오프라인·실패) 너무 최근에 보여 줬으면 기다리지 않고 바로 연다.
 * - 광고 콜백이 오지 않는 경우에도 [GUARD_MS] 뒤에는 무조건 빌드 불러오기를 진행한다.
 *
 * 광고 단위는 debug에서 구글 시험용 ID를 쓴다 (실제 광고를 누르면 정책 위반이 된다).
 */
class Ads(private val activity: Activity) {

    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var lastShownAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    /** 미리 받아 둔다. 앱을 열 때 한 번 부르면 된다. */
    fun preload() {
        runCatching {
            if (!initialized) {
                initialized = true
                MobileAds.initialize(activity) {
                    Log.i(TAG, "광고 SDK 준비 완료")
                    load()
                }
            } else {
                load()
            }
        }.onFailure { Log.w(TAG, "광고 SDK를 준비하지 못했습니다", it) }
    }

    private fun load() {
        if (loading || interstitial != null) return
        loading = true
        runCatching {
            InterstitialAd.load(
                activity,
                unitId(),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        loading = false
                        interstitial = ad
                        Log.i(TAG, "전면 광고를 받았습니다")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loading = false
                        interstitial = null
                        Log.w(TAG, "전면 광고를 받지 못했습니다: ${error.message}")
                    }
                },
            )
        }.onFailure {
            loading = false
            Log.w(TAG, "전면 광고를 요청하지 못했습니다", it)
        }
    }

    /**
     * 광고가 준비돼 있으면 보여 주고, 닫히면 [onDone]을 부른다.
     * 준비되지 않았으면 곧바로 [onDone]을 부른다 (불러오기를 막지 않는다).
     */
    fun showThenLoad(onDone: () -> Unit) {
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        val ready = ad != null && now - lastShownAt >= COOLDOWN_MS && !activity.isFinishing
        if (!ready) {
            onDone()
            preload()
            return
        }
        var done = false
        fun finish() {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(token)
            onDone()
            preload()
        }
        interstitial = null
        lastShownAt = now
        ad?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                finish()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "전면 광고를 보여 주지 못했습니다: ${error.message}")
                finish()
            }
        }
        // 광고 화면이 콜백을 주지 않는 예외 상황에서도 빌드 불러오기가 멈춰 있지 않게 한다.
        handler.postAtTime({ finish() }, token, SystemClock.uptimeMillis() + GUARD_MS)
        runCatching { ad?.show(activity) }.onFailure {
            Log.w(TAG, "전면 광고 표시 실패", it)
            finish()
        }
    }

    private fun unitId(): String =
        if (BuildConfig.DEBUG) TEST_INTERSTITIAL else BuildConfig.AD_INTERSTITIAL_UNIT

    private companion object {
        const val TAG = "Ads"
        /** 구글 공식 시험용 전면 광고 (debug 빌드에서만 쓴다) */
        const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        /** 연속으로 눌러도 광고가 겹쳐 뜨지 않게 하는 최소 간격 */
        const val COOLDOWN_MS = 2 * 60 * 1000L
        /** 광고 콜백이 오지 않을 때 빌드 불러오기를 진행하는 시한 */
        const val GUARD_MS = 12_000L
        val token = Any()

        @Volatile
        var initialized = false
    }
}
