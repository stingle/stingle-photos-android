#include <jni.h>
#include <math.h>
#include <algorithm>
#include <android/bitmap.h>
#include <android/log.h>

struct ImageConfig {
    jfloat brightness;
    jfloat contrast;
    jfloat whitePoint;
    jfloat highlights;
    jfloat shadows;
    jfloat blackPoint;
    jfloat saturation;
    jfloat warmth;
    jfloat tint;
    jfloat sharpness;
    jfloat denoise;
    jfloat vignette;

    bool hasFilter() {
        return brightness != 0.0f || contrast != 1.0f ||
        whitePoint != 0.0f || highlights != 0.0f || shadows != 0.0f || blackPoint != 0.0f ||
        saturation != 1.0f || warmth != 0.0f || tint != 0.0f;
    }

    bool hasProcess() {
        return sharpness != 0.0f || denoise != 0.0f || vignette != 0.0f;
    }
};

float clamp(float f, float min, float max) {
    return std::max(min, std::min(f, max));
}

int clamp(int f, int min, int max) {
    return std::max(min, std::min(f, max));
}

class Vec3 {
public:
    float r;
    float g;
    float b;

    Vec3(Vec3 *v) {
        this->r = v->r;
        this->g = v->g;
        this->b = v->b;
    }

    Vec3(float r, float g, float b) {
        this->r = r;
        this->g = g;
        this->b = b;
    }

    Vec3(float f) {
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

    void set(float r, float g, float b) {
        this->r = r;
        this->g = g;
        this->b = b;
    }
    
    void set(float v) {
        this->r = v;
        this->g = v;
        this->b = v;
    }

    void set(uint32_t color) {
        this->r = (color & 0xFF) / 255.0f;
        this->g = ((color >> 8) & 0xFF) / 255.0f;
        this->b = ((color >> 16) & 0xFF) / 255.0f;
    }

    void set(uint32_t* colors, int i, int j, int width, int height) {
        i = clamp(i, 0, height - 1);
        j = clamp(j, 0, width - 1);

        set(colors[i * width + j]);
    }

    void set(Vec3* v, float f) {
        this->r += (v->r - this->r) * f;
        this->g += (v->g - this->g) * f;
        this->b += (v->b - this->b) * f;
    }

    void set(float v, float f) {
        this->r += (v - this->r) * f;
        this->g += (v - this->g) * f;
        this->b += (v - this->b) * f;
    }

    float dot(Vec3 *v) {
        return r * v->r + g * v->g + b * v->b;
    }

    float dot(float f) {
        return r * f + g * f + b * f;
    }

    float dot(float r, float g, float b) {
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

    void add(float v) {
        r += v;
        g += v;
        b += v;
    }

    void add(float v, float f) {
        r += v * f;
        g += v * f;
        b += v * f;
    }

    void mul(float f) {
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

    void setToMix(Vec3* v1, Vec3* v2, float f) {
        this->r = v1->r + (v2->r - v1->r) * f;
        this->g = v1->g + (v2->g - v1->g) * f;
        this->b = v1->b + (v2->b - v1->b) * f;
    }

    void setToMix(Vec3* v1, float f) {
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
        r = clamp(r, 0.0f, 1.0f);
        g = clamp(g, 0.0f, 1.0f);
        b = clamp(b, 0.0f, 1.0f);
    }
};

float mix(float a, float b, float f) {
    return a + (b - a) * f;
}

void mix(Vec3 *a, Vec3 *b, Vec3* r, float f) {
    r->r = a->r + (b->r - a->r) * f;
    r->g = a->g + (b->g - a->g) * f;
    r->b = a->b + (b->b - a->b) * f;
}

float smoothstep(float a, float b, float x) {
    x = clamp((x - a) / (b - a), 0.0, 1.0);
    return x * x * (3.0 - 2.0 * x);
}

float dot(Vec3 *a, Vec3 *b) {
    return a->dot(b);
}

Vec3 *const tmpVec1 = new Vec3();
Vec3 *const tmpVec2 = new Vec3();
Vec3 *const tmpVec3 = new Vec3();

Vec3 *colorTemperatureToRGB(float temperature) {
//     Values from: http://blenderartists.org/forum/showthread.php?270332-OSL-Goodness&p=2268693&viewfull=1#post2268693

    if (temperature <= 6500.0f) {
        tmpVec1->set(0.0f, -2902.1955373783176f, -8257.7997278925690f);
    } else {
        tmpVec1->set(1745.0425298314172f, 1216.6168361476490f, -8257.7997278925690f);
    }

    if (temperature <= 6500.0f) {
        tmpVec2->set(0.0f, 1669.5803561666639f, 2575.2827530017594f);
    } else {
        tmpVec2->set(-2666.3474220535695f, -2173.1012343082230f, 2575.2827530017594f);
    }

    if (temperature <= 6500.0f) {
        tmpVec3->set(1.0f, 1.3302673723350029f, 1.8993753891711275f);
    } else {
        tmpVec3->set(0.55995389139931482f, 0.70381203140554553f, 1.8993753891711275f);
    }

    float clampedTemperature = clamp(temperature, 1000.0f, 40000.0f);

    tmpVec2->add(clampedTemperature);
    tmpVec1->div(tmpVec2);
    tmpVec1->add(tmpVec3);

    tmpVec1->set(1.0, smoothstep(1000.0f, 0.0f, temperature));

    return new Vec3(tmpVec1);
}

Vec3 *const hotTemperature = colorTemperatureToRGB(40000.0f);
Vec3 *const coldTemperature = colorTemperatureToRGB(2500.0f);
Vec3 *const luminanceWeighting = new Vec3(0.2125f, 0.7154f, 0.0721f);

float distance(float x1, float y1, float x2, float y2) {
    return hypot(x1 - x2, y1 - y2);
}

void applyFilters(Vec3* inColor, ImageConfig* imageConfig) {
    // Brightness
    if (imageConfig->brightness != 0.0f) {
        inColor->add(imageConfig->brightness);
        inColor->ensureLimits();
    }

    // Contrast
    if (imageConfig->contrast != 1.0f) {
        inColor->add(-0.5f);
        inColor->mul(imageConfig->contrast);
        inColor->add(0.5f);
        inColor->ensureLimits();
    }

    // Highlights and Shadows
    if (imageConfig->blackPoint != 0.0f) {
        float blackPointDistance = abs(dot(inColor, luminanceWeighting) - 0.05f);
        float blackPointWeight = pow(smoothstep(0.05f, 0.5f, blackPointDistance), 8.0f);

        inColor->add(imageConfig->blackPoint, blackPointWeight);
        inColor->ensureLimits();
    }

    // Shadow
    if (imageConfig->shadows != 0.0f) {
        float shadowDistance = abs(dot(inColor, luminanceWeighting) - 0.25f);
        float shadowWeight = pow(smoothstep(0.15f, 0.5f, shadowDistance), 4.0f);

        float shadowValue = mix(-0.1f, 0.1f, (imageConfig->shadows + 1.0f) / 2.0f);

        inColor->add(shadowValue, shadowWeight);
        inColor->ensureLimits();
    }

    // Highlight
    if (imageConfig->highlights != 0.0f) {
        float highlightDistance = abs(dot(inColor, luminanceWeighting) - 0.75f);
        float highlightWeight = pow(smoothstep(0.15f, 0.5f, highlightDistance), 4.0f);

        float highlightValue = mix(-0.1f, 0.1f, (imageConfig->highlights + 1.0f) / 2.0f);

        inColor->add(highlightValue, highlightWeight);
        inColor->ensureLimits();
    }

    // White point
    if (imageConfig->whitePoint != 0.0f) {
        float whitePointDistance = abs(dot(inColor, luminanceWeighting) - 0.95f);
        float whitePointWeight = pow(smoothstep(0.05f, 0.5f, whitePointDistance), 8.0f);

        float whitePointValue = imageConfig->whitePoint * 0.2f;

        inColor->add(whitePointValue, whitePointWeight);
        inColor->ensureLimits();
    }

    // Saturation
    if (imageConfig->saturation != 1.0f) {
        tmpVec1->set(inColor->dot(luminanceWeighting), imageConfig->saturation);
        inColor->ensureLimits();
    }

    // Warmth/Temperature
    if (imageConfig->warmth != 0.0f) {
        tmpVec1->set(1.0f);

        if (imageConfig->warmth < 0.0f) {
            tmpVec1->set(hotTemperature, (-imageConfig->warmth));
        } else {
            tmpVec1->set(coldTemperature, imageConfig->warmth);
        }

        float oldLuminance = dot(inColor, luminanceWeighting);

        inColor->mul(tmpVec1);

        float newLuminance = dot(inColor, luminanceWeighting);

        inColor->mul(oldLuminance / std::max(newLuminance, 1e-5f));
        inColor->ensureLimits();
    }

    // Tint
    if (imageConfig->tint != 0.0f) {
        float greenTint = -clamp(imageConfig->tint, -1.0f, 0.0f);
        float purpleTint = clamp(imageConfig->tint, 0.0f, 1.0f);

        inColor->g = mix(inColor->g, 1.0f, greenTint * 0.1f);

        inColor->r = mix(inColor->r, 1.0f, purpleTint * 0.1f);
        inColor->b = mix(inColor->b, 1.0f, purpleTint * 0.2f);

        inColor->ensureLimits();
    }
}

void processPixel(Vec3* inColor, int x, int y, int width, int height, uint32_t* outPixels,
                  ImageConfig* imageConfig) {
    // Sharpness
    if (imageConfig->sharpness > 0.0f) {
        float centerMultiplier = 1.0f + 4.0f * imageConfig->sharpness;
        float edgeMultiplier = imageConfig->sharpness;

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

    float dx = 0.0f;
    float dy = 0.0f;

    // Denoise
    if (imageConfig->denoise > 0.0f) {
        for (int i = 0; i < 9; i++) {
            dx = (float) (i % 3 - 1);
            dy = (float) (i / 3 - 1);

            tmpVec1->set(outPixels, (int) (y + dy), (int) (x + dx), width, height);
            tmpVec1->mul(imageConfig->denoise);

            inColor->add(tmpVec1);
        }

        inColor->mul(1.0f / (1.0f + imageConfig->denoise * 9.0f));
        inColor->ensureLimits();
    }

    // Vignette
    if (imageConfig->vignette != 0.0f) {
        float radius = std::max(width, height) / 2.0;

        dx = abs(x - width / 2.0f);
        dy = abs(y - height / 2.0f);

        float distance = hypot(dx, dy);
        float radius1 = radius * 0.4f;
        float radius2 = radius * 1.1f;

        float t = smoothstep(radius1, radius2, distance);

        if (imageConfig->vignette > 0.0f) {
            tmpVec1->set(0.0f);
        } else {
            tmpVec1->set(1.0f);
        }

        tmpVec2->set(inColor);
        tmpVec2->setToMix(tmpVec1, t);

        inColor->setToMix(tmpVec2, abs(imageConfig->vignette));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_stingle_photos_Editor_util_ImageSaver_processImage(JNIEnv *env, jobject thiz,
                                                            jobject outBitmap,
                                                            jfloat brightness, jfloat contrast,
                                                            jfloat whitePoint, jfloat highlights, jfloat shadows, jfloat blackPoint,
                                                            jfloat saturation, jfloat warmth, jfloat tint,
                                                            jfloat sharpness, jfloat denoise, jfloat vignette) {

    ImageConfig* imageConfig = new ImageConfig();
    imageConfig->brightness = brightness;
    imageConfig->contrast = contrast;
    imageConfig->whitePoint = whitePoint;
    imageConfig->highlights = highlights;
    imageConfig->shadows = shadows;
    imageConfig->blackPoint = blackPoint;
    imageConfig->saturation = saturation;
    imageConfig->warmth = warmth;
    imageConfig->tint = tint;
    imageConfig->sharpness = sharpness;
    imageConfig->denoise = denoise;
    imageConfig->vignette = vignette;

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

    if (imageConfig->hasFilter()) {
        for (int i = 0; i < outBitmapInfo.height; i++) {
            for (int j = 0; j < outBitmapInfo.width; j++) {
                color->set(outPixels, i, j, outBitmapInfo.width, outBitmapInfo.height);

                applyFilters(color, imageConfig);

                tmpPixels[i * outBitmapInfo.width + j] = color->colorInt();
            }
        }
    }

    if (imageConfig->hasProcess()) {
        for (int i = 0; i < outBitmapInfo.height; i++) {
            for (int j = 0; j < outBitmapInfo.width; j++) {
                color->set(tmpPixels, i, j, outBitmapInfo.width, outBitmapInfo.height);

                processPixel(color, j, i, outBitmapInfo.width, outBitmapInfo.height, tmpPixels,
                             imageConfig);

                outPixels[i * outBitmapInfo.width + j] &= 0xFF000000;
                outPixels[i * outBitmapInfo.width + j] |= color->colorInt();
            }
        }
    }

    delete[] tmpPixels;
    delete color;
    delete imageConfig;

    AndroidBitmap_unlockPixels(env, outBitmap);
}