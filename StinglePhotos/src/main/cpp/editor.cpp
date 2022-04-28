#include <jni.h>
#include <math.h>
#include <algorithm>
#include <android/bitmap.h>
#include <android/log.h>

double clamp(double f, double min, double max) {
    return std::max(min, std::min(f, max));
}

int clamp(int f, int min, int max) {
    return std::max(min, std::min(f, max));
}

class Vec3 {
public:
    double r;
    double g;
    double b;

    Vec3(Vec3 *v) {
        this->r = v->r;
        this->g = v->g;
        this->b = v->b;
    }

    Vec3(double r, double g, double b) {
        this->r = r;
        this->g = g;
        this->b = b;
    }

    Vec3(double f) {
        r = f;
        g = f;
        b = f;
    }
    
    Vec3() {
        r = 0.0;
        g = 0.0;
        b = 0.0;
    }
    
    void set(Vec3* v) {
        this->r = v->r;
        this->g = v->g;
        this->b = v->b;
    }

    void set(double r, double g, double b) {
        this->r = r;
        this->g = g;
        this->b = b;
    }
    
    void set(double v) {
        this->r = v;
        this->g = v;
        this->b = v;
    }

    void set(uint32_t color) {
        this->r = (color & 0xFF) / 255.0;
        this->g = ((color >> 8) & 0xFF) / 255.0;
        this->b = ((color >> 16) & 0xFF) / 255.0;
    }

    void set(uint32_t* colors, int i, int j, int width, int height) {
        i = clamp(i, 0, height - 1);
        j = clamp(j, 0, width - 1);

        set(colors[i * width + j]);
    }

    void set(Vec3* v, double f) {
        this->r += (v->r - this->r) * f;
        this->g += (v->g - this->g) * f;
        this->b += (v->b - this->b) * f;
    }

    void set(double v, double f) {
        this->r += (v - this->r) * f;
        this->g += (v - this->g) * f;
        this->b += (v - this->b) * f;
    }

    double dot(Vec3 *v) {
        return r * v->r + g * v->g + b * v->b;
    }

    double dot(double f) {
        return r * f + g * f + b * f;
    }

    double dot(double r, double g, double b) {
        return this->r * r + this->g * g + this->b * b;
    }

    void add(Vec3 *v) {
        r += v->r;
        g += v->g;
        b += v->b;
    }

    void sub(Vec3 *v) {
        r -= v->r;
        g -= v->g;
        b -= v->b;
    }

    void add(double v) {
        r += v;
        g += v;
        b += v;
    }

    void add(double v, double f) {
        r += v * f;
        g += v * f;
        b += v * f;
    }

    void mul(double f) {
        r *= f;
        g *= f;
        b *= f;
    }

    void mul(Vec3 *v) {
        r *= v->r;
        g *= v->g;
        b *= v->b;
    }

    void div(Vec3 *v) {
        r /= v->r;
        g /= v->g;
        b /= v->b;
    }

    void setToMix(Vec3* v1, Vec3* v2, double f) {
        this->r = v1->r + (v2->r - v1->r) * f;
        this->g = v1->g + (v2->g - v1->g) * f;
        this->b = v1->b + (v2->b - v1->b) * f;
    }

    void setToMix(Vec3* v1, double f) {
        this->r = this->r + (v1->r - this->r) * f;
        this->g = this->g + (v1->g - this->g) * f;
        this->b = this->b + (v1->b - this->b) * f;
    }

    uint32_t colorInt() {
        uint32_t ir = ((uint32_t) (this->r * 255) & 0xFF);
        uint32_t ig = ((uint32_t) (this->g * 255) & 0xFF) << 8;
        uint32_t ib = ((uint32_t) (this->b * 255) & 0xFF) << 16;

        return ir | ig | ib;
    }

    void ensureLimits() {
        r = clamp(r, 0.0, 1.0);
        g = clamp(g, 0.0, 1.0);
        b = clamp(b, 0.0, 1.0);
    }
};

double mix(double a, double b, double f) {
    return a + (b - a) * f;
}

void mix(Vec3 *a, Vec3 *b, Vec3* r, double f) {
    r->r = a->r + (b->r - a->r) * f;
    r->g = a->g + (b->g - a->g) * f;
    r->b = a->b + (b->b - a->b) * f;
}

double smoothstep(double a, double b, double x) {
    x = clamp((x - a) / (b - a), 0.0, 1.0);
    return x * x * (3.0 - 2.0 * x);
}

double dot(Vec3 *a, Vec3 *b) {
    return a->dot(b);
}

Vec3 *const tmpVec1 = new Vec3();
Vec3 *const tmpVec2 = new Vec3();
Vec3 *const tmpVec3 = new Vec3();

Vec3 *colorTemperatureToRGB(double temperature) {
//     Values from: http://blenderartists.org/forum/showthread.php?270332-OSL-Goodness&p=2268693&viewfull=1#post2268693

    if (temperature <= 6500.0) {
        tmpVec1->set(0.0, -2902.1955373783176, -8257.7997278925690);
    } else {
        tmpVec1->set(1745.0425298314172, 1216.6168361476490, -8257.7997278925690);
    }

    if (temperature <= 6500.0) {
        tmpVec2->set(0.0, 1669.5803561666639, 2575.2827530017594);
    } else {
        tmpVec2->set(-2666.3474220535695, -2173.1012343082230, 2575.2827530017594);
    }

    if (temperature <= 6500.0) {
        tmpVec3->set(1.0, 1.3302673723350029, 1.8993753891711275);
    } else {
        tmpVec3->set(0.55995389139931482, 0.70381203140554553, 1.8993753891711275);
    }

    double clampedTemperature = clamp(temperature, 1000.0, 40000.0);

    tmpVec2->add(clampedTemperature);
    tmpVec1->div(tmpVec2);
    tmpVec1->add(tmpVec3);

    tmpVec1->set(1.0, smoothstep(1000.0, 0.0, temperature));

    return new Vec3(tmpVec1);
}

Vec3 *const hotTemperature = colorTemperatureToRGB(40000.0);
Vec3 *const coldTemperature = colorTemperatureToRGB(2500.0);
Vec3 *const luminanceWeighting = new Vec3(0.2125, 0.7154, 0.0721);

double distance(double x1, double y1, double x2, double y2) {
    return hypot(x1 - x2, y1 - y2);
}

void applyFilters(Vec3* inColor, jfloat brightness, jfloat contrast,
                   jfloat whitePoint, jfloat highlights, jfloat shadows, jfloat blackPoint,
                   jfloat saturation, jfloat warmth, jfloat tint) {
    // Brightness
    inColor->add(brightness);
    inColor->ensureLimits();

    // Contrast
    inColor->add(-0.5);
    inColor->mul(contrast);
    inColor->add(0.5);
    inColor->ensureLimits();

    // Highlights and Shadows

    double blackPointDistance = abs(dot(inColor, luminanceWeighting) - 0.05);
    double blackPointWeight = pow(smoothstep(0.05, 0.5, blackPointDistance), 8.0);

    double blackPointValue = blackPoint < 0.0 ? blackPoint * 0.2 : blackPoint * 0.1;
    
    inColor->add(blackPoint, blackPointWeight);
    inColor->ensureLimits();

    // Shadow
    double shadowDistance = abs(dot(inColor, luminanceWeighting) - 0.25);
    double shadowWeight = pow(smoothstep(0.15, 0.5, shadowDistance), 4.0);

    double shadowValue = mix(-0.1, 0.1, (shadows + 1.0) / 2.0);

    inColor->add(shadowValue, shadowWeight);
    inColor->ensureLimits();

    // Highlight
    double highlightDistance = abs(dot(inColor, luminanceWeighting) - 0.75);
    double highlightWeight = pow(smoothstep(0.15, 0.5, highlightDistance), 4.0);

    double highlightValue = mix(-0.1, 0.1, (highlights + 1.0) / 2.0);

    inColor->add(highlightValue, highlightWeight);
    inColor->ensureLimits();

    // White point
    double whitePointDistance = abs(dot(inColor, luminanceWeighting) - 0.95);
    double whitePointWeight = pow(smoothstep(0.05, 0.5, whitePointDistance), 8.0);

    double whitePointValue = whitePoint * 0.2;

    inColor->add(whitePointValue, whitePointWeight);
    inColor->ensureLimits();

    // Saturation
    tmpVec1->set(inColor->dot(luminanceWeighting), saturation);
    inColor->ensureLimits();

    // Warmth/Temperature
    tmpVec1->set(1.0);

    if (warmth < 0.0) {
        tmpVec1->set(hotTemperature, (-warmth));
    } else {
        tmpVec1->set(coldTemperature, warmth);
    }

    double oldLuminance = dot(inColor, luminanceWeighting);

    inColor->mul(tmpVec1);

    double newLuminance = dot(inColor, luminanceWeighting);

    inColor->mul(oldLuminance / std::max(newLuminance, 1e-5));
    inColor->ensureLimits();

    // Tint
    double greenTint = -clamp(tint, -1.0, 0.0);
    double purpleTint = clamp(tint, 0.0, 1.0);

    inColor->g = mix(inColor->g, 1.0, greenTint * 0.1);

    inColor->r = mix(inColor->r, 1.0, purpleTint * 0.1);
    inColor->b = mix(inColor->b, 1.0, purpleTint * 0.2);

    inColor->ensureLimits();
}

void processPixel(Vec3* inColor, int x, int y, int width, int height, uint32_t* outPixels,
                  jfloat sharpness, jfloat denoise, jfloat vignette) {
    // Sharpness
    if (sharpness > 0.0) {
        double centerMultiplier = 1.0 + 4.0 * sharpness;
        double edgeMultiplier = sharpness;

        inColor->mul(centerMultiplier);

        tmpVec1->set(outPixels, y, x - 1, width, height);
        tmpVec1->mul(edgeMultiplier);
        inColor->sub(tmpVec1);

        tmpVec1->set(outPixels, y, x + 1, width, height);
        tmpVec1->mul(edgeMultiplier);
        inColor->sub(tmpVec1);

        tmpVec1->set(outPixels, y - 1, x, width, height);
        tmpVec1->mul(edgeMultiplier);
        inColor->sub(tmpVec1);

        tmpVec1->set(outPixels, y + 1, x, width, height);
        tmpVec1->mul(edgeMultiplier);
        inColor->sub(tmpVec1);

        inColor->ensureLimits();
    }

    double dx = 0.0;
    double dy = 0.0;

    // Denoise
    if (denoise > 0.0) {
        for (int i = 0; i < 9; i++) {
            dx = (float) (i % 3 - 1);
            dy = (float) (i / 3 - 1);

            tmpVec1->set(outPixels, (int) (y + dy), (int) (x + dx), width, height);
            tmpVec1->mul(denoise);

            inColor->add(tmpVec1);
        }

        inColor->mul(1.0 / (1.0 + denoise * 9.0));
        inColor->ensureLimits();
    }

    double radius = std::max(width, height) / 2.0;

    dx = abs(x - width / 2.0);
    dy = abs(y - height / 2.0);

    // Vignette
    double distance = hypot(dx, dy);
    double radius1 = radius * 0.4;
    double radius2 = radius * 1.1;

    double t = smoothstep(radius1, radius2, distance);

    if (vignette > 0.0) {
        tmpVec1->set(0.0);
    } else {
        tmpVec1->set(1.0);
    }

    tmpVec2->set(inColor);
    tmpVec2->setToMix(tmpVec1, t);

    inColor->setToMix(tmpVec2, abs(vignette));
}

extern "C" JNIEXPORT void JNICALL
Java_org_stingle_photos_Editor_util_ImageSaver_processImage(JNIEnv *env, jobject thiz,
                                                            jobject outBitmap,
                                                            jfloat brightness, jfloat contrast,
                                                            jfloat whitePoint, jfloat highlights, jfloat shadows, jfloat blackPoint,
                                                            jfloat saturation, jfloat warmth, jfloat tint,
                                                            jfloat sharpness, jfloat denoise, jfloat vignette) {

    AndroidBitmapInfo outBitmapInfo;

    uint32_t *outPixels;

    void *outDataPointer;

    int result;

    if ((result = AndroidBitmap_getInfo(env, outBitmap, &outBitmapInfo)) < 0) {
        return;
    }

    if ((result = AndroidBitmap_lockPixels(env, outBitmap, &outDataPointer)) < 0) {
        return;
    }

    outPixels = (uint32_t *) outDataPointer;

    uint32_t* tmpPixels = new uint32_t[outBitmapInfo.height * outBitmapInfo.width];

    Vec3* color = new Vec3();

    for (int i = 0; i < outBitmapInfo.height; i++) {
        for (int j = 0; j < outBitmapInfo.width; j++) {
            color->set(outPixels, i, j, outBitmapInfo.width, outBitmapInfo.height);

            applyFilters(color, brightness, contrast, whitePoint, highlights, shadows, blackPoint, saturation, warmth, tint);

            tmpPixels[i * outBitmapInfo.width + j] = color->colorInt();
        }
    }

    for (int i = 0; i < outBitmapInfo.height; i++) {
        for (int j = 0; j < outBitmapInfo.width; j++) {
            color->set(tmpPixels, i, j, outBitmapInfo.width, outBitmapInfo.height);

            processPixel(color, j, i, outBitmapInfo.width, outBitmapInfo.height, tmpPixels, sharpness, denoise, vignette);

            outPixels[i * outBitmapInfo.width + j] &= 0xFF000000;
            outPixels[i * outBitmapInfo.width + j] |= color->colorInt();
        }
    }

    delete[] tmpPixels;
    delete color;

    AndroidBitmap_unlockPixels(env, outBitmap);
}