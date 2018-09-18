package com.github.benmanes.caffeine.cache;

import com.github.benmanes.caffeine.cache.testing.CacheContext;
import com.github.benmanes.caffeine.cache.testing.CacheProvider;
import com.github.benmanes.caffeine.cache.testing.CacheSpec;
import com.github.benmanes.caffeine.cache.testing.CacheValidationListener;
import org.junit.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.Map;

import static com.github.benmanes.caffeine.cache.testing.CacheSpec.Expiration.AFTER_WRITE;
import static com.github.benmanes.caffeine.cache.testing.CacheSpec.Expiration.VARIABLE;

@Listeners(CacheValidationListener.class)
@Test(dataProviderClass = CacheProvider.class)
public class TestEdenResize {
    @Test(dataProvider = "caches")
    @CacheSpec(mustExpireWithAnyOf = {AFTER_WRITE, VARIABLE},
            maximumSize = CacheSpec.Maximum.TEN,
            expireAfterWrite = {CacheSpec.Expire.DISABLED, CacheSpec.Expire.ONE_MINUTE},
            expiry = {CacheSpec.CacheExpiry.WRITE}, expiryTime = CacheSpec.Expire.ONE_MINUTE,
            population = {CacheSpec.Population.PARTIAL, CacheSpec.Population.FULL})
    public void TestResizingEden(Cache<Integer, Integer> cache, CacheContext context) {
        cache.put(1, 1);
        Policy.Eviction<Integer, Integer> eviction = cache.policy().eviction().orElse(null);
        Assert.assertNotNull(cache.getIfPresent(1));
        Assert.assertTrue("Failed to set percentMain to 0.5", cleanlySetPercentMain(eviction, 0.5d));
        Assert.assertNotNull(cache.getIfPresent(1));
        Assert.assertTrue("Failed to set percentMain to 0", cleanlySetPercentMain(eviction, 0d));
        Assert.assertNotNull(cache.getIfPresent(1));
        Assert.assertTrue("Failed to set percentMain to 1", cleanlySetPercentMain(eviction, 1d));
        Assert.assertNotNull(cache.getIfPresent(1));
    }

    private boolean cleanlySetPercentMain(Policy.Eviction<Integer, Integer> eviction, double percentMain) {
        Assert.assertNotNull(eviction);
        try {
            eviction.setMaximum(eviction.getMaximum(), percentMain);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Test(dataProvider = "caches")
    @CacheSpec(mustExpireWithAnyOf = {AFTER_WRITE, VARIABLE},
            maximumSize = CacheSpec.Maximum.TEN,
            expireAfterWrite = {CacheSpec.Expire.DISABLED, CacheSpec.Expire.ONE_MINUTE},
            expiry = {CacheSpec.CacheExpiry.WRITE}, expiryTime = CacheSpec.Expire.ONE_MINUTE,
            population = {CacheSpec.Population.PARTIAL, CacheSpec.Population.FULL})
    public void TestPercentageMainOutOfBounds(Cache<Integer, Integer> cache, CacheContext context) {
        Policy.Eviction<Integer, Integer> eviction = cache.policy().eviction().orElse(null);
        Assert.assertFalse("Failed to throw for percentMain < 0", cleanlySetPercentMain(eviction, -1d));
        Assert.assertFalse("Failed to throw for percentMain > 1", cleanlySetPercentMain(eviction, 1.01d));
    }

    @Test(dataProvider = "caches")
    @CacheSpec(mustExpireWithAnyOf = {AFTER_WRITE, VARIABLE},
            maximumSize = CacheSpec.Maximum.TEN,
            expireAfterWrite = {CacheSpec.Expire.DISABLED, CacheSpec.Expire.ONE_MINUTE},
            expiry = {CacheSpec.CacheExpiry.WRITE}, expiryTime = CacheSpec.Expire.ONE_MINUTE,
            population = {CacheSpec.Population.PARTIAL, CacheSpec.Population.FULL})
    public void Test100PercentWindow(Cache<Integer, Integer> cache, CacheContext context) {
        Policy.Eviction<Integer, Integer> eviction = cache.policy().eviction().get();
        eviction.setMaximum(eviction.getMaximum(), 0d);
        for (int counter = 0; counter < 20; ++counter) {
            cache.put(counter, counter);
        }

        // if the window is 100% of the cache, the last 10 entries should be in the cache.
        Map<Integer, Integer> cm = cache.asMap();
        for (int counter = 10; counter < 20; ++counter) {
            Integer val = cache.getIfPresent(counter);
            Assert.assertNotNull(String.format("Failed to find key %d", counter), val);
        }
    }

    @Test(dataProvider = "caches")
    @CacheSpec(mustExpireWithAnyOf = {AFTER_WRITE, VARIABLE},
            maximumSize = CacheSpec.Maximum.TEN,
            weigher = {CacheSpec.CacheWeigher.DEFAULT, CacheSpec.CacheWeigher.TEN},
            expireAfterWrite = {CacheSpec.Expire.DISABLED, CacheSpec.Expire.ONE_MINUTE},
            expiry = {CacheSpec.CacheExpiry.WRITE}, expiryTime = CacheSpec.Expire.ONE_MINUTE,
            population = {CacheSpec.Population.PARTIAL, CacheSpec.Population.FULL})
    public void Test50PercentWindow(Cache<Integer, Integer> cache, CacheContext context) {
        Policy.Eviction<Integer, Integer> eviction = cache.policy().eviction().get();
        eviction.setMaximum(eviction.getMaximum(), .5d);
        for (int counter = 0; counter < 20; ++counter) {
            cache.put(counter, counter);
        }

        // if the window is 50% of the cache, the last 5 entries should be in the cache.
        Map<Integer, Integer> cm = cache.asMap();
        for (int counter = 15; counter < 20; ++counter) {
            Assert.assertTrue(cm.keySet().contains(counter));
        }

        // some keys between 10 and 15 will be missing because they didn't make it to the main cache
        boolean missing = false;
        for (int counter = 10; counter < 15; ++counter) {
            if (!cm.keySet().contains(counter)) {
                missing = true;
            }
        }
        Assert.assertTrue("All keys made it to the main cache!", missing);
    }
}
