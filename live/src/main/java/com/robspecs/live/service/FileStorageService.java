package com.robspecs.live.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile; // यदि फ़ाइल अपलोड की आवश्यकता हो तो
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface FileStorageService {

    /**
     * स्ट्रीम से एक फ़ाइल को स्टोरेज में स्टोर करता है।
     *
     * @param inputStream फ़ाइल का इनपुट स्ट्रीम।
     * @param fileName फ़ाइल का नाम (इसमें एक्सटेंशन शामिल होना चाहिए)।
     * @param userId फ़ाइल को अपलोड करने वाले उपयोगकर्ता की आईडी (फ़ोल्डर संरचना के लिए)।
     * @param subDirectory उपयोगकर्ता के फ़ोल्डर के भीतर अतिरिक्त सब-डायरेक्टरी (जैसे "raw", "processed")।
     * @return स्टोरेज में फ़ाइल का सापेक्ष पाथ।
     * @throws IOException यदि फ़ाइल को स्टोर करने में कोई त्रुटि आती है।
     */
    String storeFile(InputStream inputStream, String fileName, Long userId, String subDirectory) throws IOException;

    /**
     * स्टोरेज से फ़ाइल को एक Spring Resource के रूप में लोड करता है।
     *
     * @param relativePath स्टोरेज में फ़ाइल का सापेक्ष पाथ।
     * @return फ़ाइल के लिए Resource ऑब्जेक्ट।
     * @throws IOException यदि फ़ाइल नहीं मिलती है या पढ़ने योग्य नहीं है।
     */
    Resource loadFileAsResource(String relativePath) throws IOException;

    /**
     * स्टोरेज से फ़ाइल को हटाता है।
     *
     * @param relativePath हटाई जाने वाली फ़ाइल का सापेक्ष पाथ।
     * @return true यदि फ़ाइल सफलतापूर्वक हटाई जाती है, अन्यथा false।
     * @throws IOException यदि फ़ाइल को हटाने में कोई त्रुटि आती है।
     */
    boolean deleteFile(String relativePath) throws IOException;

    /**
     * स्टोरेज से एक डायरेक्टरी और उसकी सभी सामग्री को हटाता है।
     *
     * @param relativePath हटाई जाने वाली डायरेक्टरी का सापेक्ष पाथ।
     * @return true यदि डायरेक्टरी सफलतापूर्वक हटाई जाती है, अन्यथा false।
     * @throws IOException यदि डायरेक्टरी को हटाने में कोई त्रुटि आती है।
     */
    boolean deleteDirectory(String relativePath) throws IOException;

    /**
     * जाँच करता है कि स्टोरेज में कोई फ़ाइल मौजूद है या नहीं।
     *
     * @param relativePath जाँच की जाने वाली फ़ाइल का सापेक्ष पाथ।
     * @return true यदि फ़ाइल मौजूद है, अन्यथा false।
     */
    boolean doesFileExist(String relativePath);

    /**
     * स्टोरेज में एक फ़ाइल का साइज़ बाइट्स में प्राप्त करता है।
     *
     * @param relativePath साइज़ प्राप्त की जाने वाली फ़ाइल का सापेक्ष पाथ।
     * @return फ़ाइल का साइज़ बाइट्स में।
     * @throws IOException यदि फ़ाइल नहीं मिलती है या पढ़ने योग्य नहीं है।
     */
    long getFileSize(String relativePath) throws IOException;

    /**
     * स्टोरेज में एक फ़ाइल के लिए Java NIO Path ऑब्जेक्ट प्राप्त करता है।
     *
     * @param relativePath पाथ प्राप्त की जाने वाली फ़ाइल का सापेक्ष पाथ।
     * @return फ़ाइल के लिए Path ऑब्जेक्ट।
     */
    Path getFilePath(String relativePath);
}