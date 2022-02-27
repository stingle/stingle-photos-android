varying highp vec2 textureCoordinate;

uniform sampler2D inputImageTexture;

uniform highp float textureWidth;
uniform highp float textureHeight;

uniform lowp float brightness;
uniform lowp float contrast;
uniform lowp float highlights;
uniform lowp float shadows;
uniform lowp float saturation;
uniform highp float warmth;
uniform lowp float sharpness;
uniform lowp float vignette;

uniform highp vec2 cropCenter;
uniform highp float cropRotation;
uniform highp float cropWidth;
uniform highp float cropHeight;

uniform float dimAmount;

vec3 colorTemperatureToRGB(highp float temperature) {
//     Values from: http://blenderartists.org/forum/showthread.php?270332-OSL-Goodness&p=2268693&viewfull=1#post2268693

    mat3 m = (temperature <= 6500.0) ? mat3(vec3(0.0, -2902.1955373783176, -8257.7997278925690),
                                            vec3(0.0, 1669.5803561666639, 2575.2827530017594),
                                            vec3(1.0, 1.3302673723350029, 1.8993753891711275)) :
                                        mat3(vec3(1745.0425298314172, 1216.6168361476490, -8257.7997278925690),
                                            vec3(-2666.3474220535695, -2173.1012343082230, 2575.2827530017594),
                                            vec3(0.55995389139931482, 0.70381203140554553, 1.8993753891711275));

    return mix(clamp(vec3(m[0] / (vec3(temperature) + m[1]) + m[2]), vec3(0.0), vec3(1.0)), vec3(1.0), 0.2);
}

vec3 applyFilters(vec3 textureColor) {
    // Brightness
    vec3 outColor = clamp(textureColor.rgb + vec3(brightness), 0.0, 1.0);

    // Contrast
    outColor = clamp((outColor.rgb - vec3(0.5)) * contrast + vec3(0.5), vec3(0.0, 0.0, 0.0), vec3(1.0, 1.0, 1.0));

    // Highlights and Shadows

    mediump float luminance = dot(outColor.rgb, vec3(0.3, 0.3, 0.3));

    mediump float highlight = clamp((1.0 - (pow(1.0 - luminance, 1.0 / (2.0 - highlights)) +
    -0.8 * pow(1.0 - luminance, 2.0/(2.0 - highlights)))) - luminance, -1.0, 0.0);

    mediump float shadow = clamp((pow(luminance, 1.0 / (shadows + 1.0)) + -0.76 * pow(luminance,
    2.0 / (shadows + 1.0))) - luminance, 0.0, 1.0);

    outColor = (luminance + shadow + highlight) * (outColor / (luminance - 0.0));

    // Saturation

    luminance = dot(outColor.rgb, vec3(0.2125, 0.7154, 0.0721));

    lowp vec3 greyScaleColor = vec3(luminance);

    outColor = mix(greyScaleColor, outColor, saturation);

    // Warmth/Temperature

    highp vec3 coldTemperature = colorTemperatureToRGB(40000.0);
    highp vec3 hotTemperature = colorTemperatureToRGB(2500.0);
    highp vec3 selectedTemperature = warmth < 0.0 ? mix(coldTemperature, vec3(1.0), warmth + 1.0) : mix(vec3(1.0), hotTemperature, warmth);

    highp float oldLuminance = dot(outColor, vec3(0.2126, 0.7152, 0.0722));

    outColor = outColor * selectedTemperature;

    highp float newLuminance = dot(outColor, vec3(0.2126, 0.7152, 0.0722));

    outColor *= oldLuminance / max(newLuminance, 1e-5);

    return outColor;
}

void main() {
    lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);

    vec3 outColor = applyFilters(textureColor.rgb);

    // Sharpness
    vec3 leftTextureColor = applyFilters(texture2D(inputImageTexture, vec2(textureCoordinate.x - 1.0 / textureWidth, textureCoordinate.y)).rgb);
    vec3 rightTextureColor = applyFilters(texture2D(inputImageTexture, vec2(textureCoordinate.x + 1.0 / textureWidth, textureCoordinate.y)).rgb);
    vec3 topTextureColor = applyFilters(texture2D(inputImageTexture, vec2(textureCoordinate.x, textureCoordinate.y - 1.0 / textureHeight)).rgb);
    vec3 bottomTextureColor = applyFilters(texture2D(inputImageTexture, vec2(textureCoordinate.x, textureCoordinate.y + 1.0 / textureHeight)).rgb);

    float centerMultiplier = 1.0 + 4.0 * sharpness;
    float edgeMultiplier = sharpness;

    outColor = outColor * centerMultiplier - (leftTextureColor * edgeMultiplier + rightTextureColor * edgeMultiplier + topTextureColor * edgeMultiplier + bottomTextureColor * edgeMultiplier);

    highp float radius = max(cropWidth, cropHeight) / 2.0;

    highp float sin = sin(cropRotation);
    highp float cos = cos(cropRotation);

    highp float x = textureCoordinate.x * textureWidth - cropCenter.x;
    highp float y = textureCoordinate.y * textureHeight - cropCenter.y;

    highp float rotatedX = x * cos - y * sin;
    highp float rotatedY = x * sin + y * cos;

    highp float a = dimAmount;

    highp float dx = abs(rotatedX);
    highp float dy = abs(rotatedY);

    if (dx <= cropWidth / 2.0 && dy <= cropHeight / 2.0) {
        a = 1.0;
    }

    // Vignette

    highp float distance = sqrt(dx * dx + dy * dy);
    highp float radius1 = radius * 0.4;
    highp float radius2 = radius * 1.1;

    highp float t = smoothstep(radius1, radius2, distance);

    vec3 vignettedColor = outColor * (1.0 - t);

    outColor = mix(outColor, vignettedColor, vignette);

    gl_FragColor = vec4(outColor * a, textureColor.a);
}