/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakTracker;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

/**
 * Skeletal {@link ByteBufAllocator} implementation to extend.
 */
public abstract class AbstractByteBufAllocator implements ByteBufAllocator {
    static final int DEFAULT_INITIAL_CAPACITY = 256;
    static final int DEFAULT_MAX_CAPACITY = Integer.MAX_VALUE;
    static final int DEFAULT_MAX_COMPONENTS = 16;
    static final int CALCULATE_THRESHOLD = 1048576 * 4; // 4 MiB page

    static {
        ResourceLeakDetector.addExclusions(AbstractByteBufAllocator.class, "toLeakAwareBuffer");
    }

    protected static ByteBuf toLeakAwareBuffer(ByteBuf buf) {
        ResourceLeakTracker<ByteBuf> leak;
        switch (ResourceLeakDetector.getLevel()) {
            case SIMPLE:
                leak = AbstractByteBuf.leakDetector.track(buf);
                if (leak != null) {
                    buf = new SimpleLeakAwareByteBuf(buf, leak);
                }
                break;
            case ADVANCED:
            case PARANOID:
                leak = AbstractByteBuf.leakDetector.track(buf);
                if (leak != null) {
                    buf = new AdvancedLeakAwareByteBuf(buf, leak);
                }
                break;
            default:
                break;
        }
        return buf;
    }

    protected static CompositeByteBuf toLeakAwareBuffer(CompositeByteBuf buf) {
        ResourceLeakTracker<ByteBuf> leak;
        switch (ResourceLeakDetector.getLevel()) {
            case SIMPLE:
                leak = AbstractByteBuf.leakDetector.track(buf);
                if (leak != null) {
                    buf = new SimpleLeakAwareCompositeByteBuf(buf, leak);
                }
                break;
            case ADVANCED:
            case PARANOID:
                leak = AbstractByteBuf.leakDetector.track(buf);
                if (leak != null) {
                    buf = new AdvancedLeakAwareCompositeByteBuf(buf, leak);
                }
                break;
            default:
                break;
        }
        return buf;
    }

    private final boolean directByDefault;
    private final ByteBuf emptyBuf;

    /**
     * Instance use heap buffers by default
     */
    protected AbstractByteBufAllocator() {
        this(false);
    }

    /**
     * Create new instance
     *
     * @param preferDirect {@code true} if {@link #buffer(int)} should try to allocate a direct buffer rather than
     *                     a heap buffer
     */
    protected AbstractByteBufAllocator(boolean preferDirect) {
        directByDefault = preferDirect && PlatformDependent.hasUnsafe();
        emptyBuf = new EmptyByteBuf(this);
    }

    @Override
    public ByteBuf buffer() {
        if (directByDefault) {
            return directBuffer();
        }
        return heapBuffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        return heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        if (PlatformDependent.hasUnsafe() || isDirectBufferPooled()) {
            return directBuffer(DEFAULT_INITIAL_CAPACITY);
        }
        return heapBuffer(DEFAULT_INITIAL_CAPACITY);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
        if (PlatformDependent.hasUnsafe() || isDirectBufferPooled()) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
        if (PlatformDependent.hasUnsafe() || isDirectBufferPooled()) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        return heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        return heapBuffer(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return heapBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        validate(initialCapacity, maxCapacity);
        return newHeapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return directBuffer(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return directBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        validate(initialCapacity, maxCapacity);
        return newDirectBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
        if (directByDefault) {
            return compositeDirectBuffer();
        }
        return compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
        if (directByDefault) {
            return compositeDirectBuffer(maxNumComponents);
        }
        return compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
        return compositeHeapBuffer(DEFAULT_MAX_COMPONENTS);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return toLeakAwareBuffer(new CompositeByteBuf(this, false, maxNumComponents));
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
        return compositeDirectBuffer(DEFAULT_MAX_COMPONENTS);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return toLeakAwareBuffer(new CompositeByteBuf(this, true, maxNumComponents));
    }

    private static void validate(int initialCapacity, int maxCapacity) {
        checkPositiveOrZero(initialCapacity, "initialCapacity");
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity: %d (expected: not greater than maxCapacity(%d)",
                    initialCapacity, maxCapacity));
        }
    }

    /**
     * Create a heap {@link ByteBuf} with the given initialCapacity and maxCapacity.
     */
    protected abstract ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity);

    /**
     * Create a direct {@link ByteBuf} with the given initialCapacity and maxCapacity.
     */
    protected abstract ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity);

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(directByDefault: " + directByDefault + ')';
    }

    /**
     * 采用倍增或者步进算法的原因如下：如果以minNewCapacity作为目标容量，则本次扩容后的可写字节数刚好够本次写入使用。
     * 写入完成后，它的可写字节数会变为0，下次做写入操作的时候，需要再次动态扩张。
     * 这样就会形成第一次动态扩张后，每次写入操作都会进行动态扩张，由于动态扩张需要进行内存复制，频繁的内存复制会导致性能下降。
     * 采用先倍增后步进的原因如下：当内存比较小的情况下，倍增操作并不会带来太多的内存浪费，例如64字节-->128字节-->256字节，这样的内存扩张方式对于大多数应用系统是可以接受的。
     * 但是，当内存增长到一定阈值后，再进行倍增就可能会带来额外的内存浪费，例如10MB，采用倍增后变为20MB。
     * 但很有可能系统只需要12MB，则扩张到20MB后会带来8MB的内存浪费。
     * 由于每个客户端连接都可能维护自已独立的接收和发送缓冲区，这样随着客户读的线性增长，内存浪费也会成比例地增加，因此，达到某个阙值后就需要以步进的方式对内存进行平滑的扩张。
     *
     * 这个阈值是个经验值，不同的应用场景，这个值可能不同，此处，ByteBuf取值为4MB。
     * @param minNewCapacity
     * @param maxCapacity
     * @return
     */
    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        checkPositiveOrZero(minNewCapacity, "minNewCapacity");
        if (minNewCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "minNewCapacity: %d (expected: not greater than maxCapacity(%d)",
                    minNewCapacity, maxCapacity));
        }
        final int threshold = CALCULATE_THRESHOLD; // 4 MiB page

        // 当需要的新容量正好等于门限阀值时，使用阀值作为新的缓冲区容量
        if (minNewCapacity == threshold) {
            return threshold;
        }

        // If over threshold, do not double but just increase by threshold.
        // 如果新申请的内存空间大于阈值，不能采用倍增的方式（防止内存膨胀和浪费）扩张内存，而采用每次步进4MB的方式进行内存扩张。扩张的时候需要对扩张后的
        // 内存和最大内存（maxCapacity）进行比较，如果大于缓冲区的最大长度，则使用maxCapacity作为扩容后的缓冲区容量
        if (minNewCapacity > threshold) {
            int newCapacity = minNewCapacity / threshold * threshold;
            if (newCapacity > maxCapacity - threshold) {
                newCapacity = maxCapacity;
            } else {
                newCapacity += threshold;
            }
            return newCapacity;
        }

        // 64 <= newCapacity is a power of 2 <= threshold
        // 如果扩容后的新容量小于阀值，则以64为基数进行倍增，直到倍增后的结果大于或等于需要的容量值
        final int newCapacity = MathUtil.findNextPositivePowerOfTwo(Math.max(minNewCapacity, 64));
        return Math.min(newCapacity, maxCapacity);
    }
}
