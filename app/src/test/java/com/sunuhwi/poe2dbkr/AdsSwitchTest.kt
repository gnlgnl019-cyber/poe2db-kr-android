package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 광고 스위치 확인.
 *
 * 테스트 기간 빌드에서는 광고를 요청도 표시도 하지 않는다.
 * 정식 출시 때 켜면 이 테스트도 같이 손봐야 한다.
 */
class AdsSwitchTest {

    @Test
    fun 광고_스위치는_꺼져_있다() {
        assertFalse(BuildConfig.ENABLE_ADS)
    }
}
