package com.kazikonnect.backend.core.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public Map<?, ?> upload(MultipartFile file, String folder) throws IOException {
        return cloudinary.uploader().upload(file.getBytes(), 
            ObjectUtils.asMap("folder", folder));
    }

    public Map<?, ?> delete(String publicId) throws IOException {
        try {
            return cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            return ObjectUtils.emptyMap();
        }
    }
}
