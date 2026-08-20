#!/usr/bin/env python3
# ============================================================
# 长截图（full-page scroll screenshot）—— 零应用改动，纯 adb 驱动。
#
# 方案 A（docs/research/actions-scroll-screenshot.md 决策）：滚动 N 屏 +
# 相邻帧中央竖条像素比对找重叠偏移 + PIL 拼接。找不到可靠重叠时显式
# 失败（exit 1），由调用方 screenshots.sh 退化为单帧视口截图，不阻断 job。
#
# 复用实证算法（wear-explorer / seeway-jietu）：关动画 → screencap →
# swipe up → 等待稳定 → 再 screencap 循环；重叠检测用中央竖条避开
# 状态栏/导航栏/滚动条。
#
# 用法：long_screenshot.py OUT.png [MAX_FRAMES] [SWIPE_UP_DELTA]
# ============================================================
import os
import sys
import time
import subprocess
import tempfile


def ensure_deps():
    """Pillow + numpy 优先复用；缺失则 best-effort 安装。"""
    try:
        from PIL import Image  # noqa: F401
        import numpy  # noqa: F401
    except Exception:
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "--quiet", "Pillow", "numpy"],
            check=False,
        )
    from PIL import Image
    import numpy

    return Image, numpy


def adb(args, check=True):
    return subprocess.run(["adb"] + args, capture_output=True, check=check)


def screencap(path):
    r = adb(["exec-out", "screencap", "-p"])
    if r.returncode != 0 or not r.stdout:
        raise RuntimeError("screencap failed")
    with open(path, "wb") as f:
        f.write(r.stdout)


def swipe_up(delta=1600):
    # pixel_6 1080x2400：自底上滑 delta 像素触发内容滚动（Compose LazyColumn /
    # WebView 外层 verticalScroll 均整视口平移，内容确定）
    subprocess.run(
        ["adb", "shell", "input", "swipe", "540", "2000", "540", str(2000 - delta), "250"],
        check=False,
    )


def find_overlap(prev_arr, arr, h):
    """中央竖条（x∈[15%,85%]）比对 prev 底 o 行与 cur 顶 o 行，返回 (best_o, best_d)。"""
    x0 = int(prev_arr.shape[1] * 0.15)
    x1 = int(prev_arr.shape[1] * 0.85)
    sp = prev_arr[:, x0:x1, :]
    sc = arr[:, x0:x1, :]
    best_o, best_d = h - 1, 1e18
    lo = max(1, int(h * 0.2))
    for o in range(lo, h):
        d = float((sp[h - o : h] - sc[0:o]).astype("int32").abs().mean())
        if d < best_d:
            best_d, best_o = d, o
    return best_o, best_d


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "artifacts/screenshots/long.png"
    max_frames = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    delta = int(sys.argv[3]) if len(sys.argv) > 3 else 1600

    Image, np = ensure_deps()

    # 关动画，减少滚动条/转场造成的假重叠
    for s in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb(["shell", "settings", "put", "global", s, "0"], check=False)

    tmp = tempfile.mkdtemp(prefix="longshot_")
    prev_arr = None
    stitched = None

    for i in range(max_frames):
        cur_png = os.path.join(tmp, f"f{i}.png")
        screencap(cur_png)
        arr = np.asarray(Image.open(cur_png).convert("RGB"), dtype="int32")
        h = arr.shape[0]
        if prev_arr is None:
            stitched = arr.copy()
            prev_arr = arr
        else:
            best_o, best_d = find_overlap(prev_arr, arr, h)
            # 无可靠重叠 → 拼接不可信，失败交回退
            if best_d > 40:
                print(f"::warning::no reliable overlap (min diff={best_d:.1f}); abort stitch", file=sys.stderr)
                return 1
            # 重叠≈整屏且差异≈0 → 已到底，停止
            if best_o >= h - 2 and best_d < 2:
                break
            stitched = np.concatenate([stitched, arr[best_o:h]], axis=0)
            prev_arr = arr
        # 准备下一帧：上滑（末帧无需）
        if i < max_frames - 1:
            swipe_up(delta)
            time.sleep(1.5)  # 滚动条淡出 + 内容稳定

    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    Image.fromarray(stitched.astype("uint8")).save(out)
    print(f"long screenshot stitched -> {out} ({stitched.shape[0]}px)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
