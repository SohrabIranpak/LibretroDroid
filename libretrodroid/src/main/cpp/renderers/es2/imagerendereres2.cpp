/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "imagerendereres2.h"
#include "../../libretro/libretro.h"

LibretroDroid::ImageRendererES2::ImageRendererES2() {
    glGenTextures(1, &currentTexture);
    glBindTexture(GL_TEXTURE_2D, currentTexture);
}

void LibretroDroid::ImageRendererES2::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    glBindTexture(GL_TEXTURE_2D, currentTexture);

    glPixelStorei(GL_UNPACK_ALIGNMENT, bytesPerPixel);

    if (pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
        convertDataFromRGB8888(data, pitch * height);
    }

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    if (lastFrameSize.first != width || lastFrameSize.second != height) {
        glTexImage2D(GL_TEXTURE_2D, 0, glInternalFormat, width, height, 0, glFormat, glType, NULL);
    }

    // If the given texture has the correct size we just upload it.
    if (bytesPerPixel * width == pitch) {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, glFormat, glType, data);
    } else {
        // Here we are forced to take the long and slow way to upload the padded texture.
        for (int i = 0; i < height; i++) {
            auto row = (char*) data + (pitch) * i;
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, i, width, 1, glFormat, glType, row);
        }
    }

    glBindTexture(GL_TEXTURE_2D, 0);

    LibretroDroid::Renderer::onNewFrame(data, width, height, pitch);
}

void LibretroDroid::ImageRendererES2::convertDataFromRGB8888(const void *data, size_t size) {
    char* pixelData = (char*) data;

    for (int i = 0; i < size - 4; i += 4) {
        auto currentRed = pixelData[i + 0];
        auto currentBlue = pixelData[i + 2];
        pixelData[i + 0] = currentBlue;
        pixelData[i + 2] = currentRed;
    }
}

uintptr_t LibretroDroid::ImageRendererES2::getTexture() {
    return currentTexture;
}

uintptr_t LibretroDroid::ImageRendererES2::getFramebuffer() {
    return 0; // ImageRender does not really expose a framebuffer.
}

void LibretroDroid::ImageRendererES2::setPixelFormat(int pixelFormat) {
    this->pixelFormat = pixelFormat;

    switch (pixelFormat) {
        case RETRO_PIXEL_FORMAT_XRGB8888:
            this->glInternalFormat = GL_RGBA;
            this->glFormat = GL_RGBA;
            this->glType = GL_UNSIGNED_BYTE;
            this->bytesPerPixel = 4;
            break;

        default:
        case RETRO_PIXEL_FORMAT_RGB565:
            this->glInternalFormat = GL_RGB565;
            this->glFormat = GL_RGB;
            this->glType = GL_UNSIGNED_SHORT_5_6_5;
            this->bytesPerPixel = 2;
            break;
    }
}