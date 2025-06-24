package com.robspecs.live.service;

import com.robspecs.live.dto.VideoDetailsDTO;
import com.robspecs.live.entities.User;
import com.robspecs.live.entities.Video;
import com.robspecs.live.exceptions.FileNotFoundException;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface VideoService {

    /**
     * डेटाबेस में एक नया लाइव स्ट्रीम रिकॉर्ड बनाता है जब एक लाइव सेशन कैप्चर/रिकॉर्ड किया जाता है।
     * यह विधि स्ट्रीमिंग पाइपलाइन द्वारा आंतरिक रूप से ट्रिगर होती है।
     * @param streamId लाइव स्ट्रीम सेशन की अद्वितीय आईडी।
     * @param currentUser लाइव स्ट्रीम का मालिक उपयोगकर्ता।
     * @param originalFilePath वह पाथ जहाँ लाइव स्ट्रीम की कच्ची रिकॉर्डिंग संग्रहीत है।
     * @return नए बनाए गए रिकॉर्ड का प्रतिनिधित्व करने वाला VideoDetailsDTO।
     */
    VideoDetailsDTO createLiveStreamRecord(String streamId, User currentUser, String originalFilePath);

    /**
     * एक लाइव स्ट्रीम रिकॉर्ड और उसकी संबद्ध कच्ची फ़ाइल और *प्रॉसेस्ड HLS फ़ाइलों* को स्टोरेज से हटाता है।
     * इसके लिए वर्तमान उपयोगकर्ता का मालिक होना आवश्यक है।
     * @param videoId हटाए जाने वाले लाइव स्ट्रीम रिकॉर्ड की आईडी।
     * @param currentUser प्रमाणीकृत उपयोगकर्ता।
     * @throws FileNotFoundException यदि रिकॉर्ड नहीं मिलता है या उपयोगकर्ता के पास उसका स्वामित्व नहीं है।
     * @throws RuntimeException यदि स्टोरेज से फ़ाइलों को हटाने में कोई त्रुटि आती है।
     */
    void deleteLiveStreamRecord(Long videoId, User currentUser);

    /**
     * मालिक के लिए एक विशिष्ट लाइव स्ट्रीम रिकॉर्ड का विवरण प्राप्त करता है।
     * @param videoId प्राप्त किए जाने वाले लाइव स्ट्रीम रिकॉर्ड की आईडी।
     * @param currentUser विवरण का अनुरोध करने वाला प्रमाणीकृत उपयोगकर्ता।
     * @return रिकॉर्ड की जानकारी युक्त VideoDetailsDTO।
     * @throws FileNotFoundException यदि रिकॉर्ड नहीं मिलता है या उपयोगकर्ता के पास उसका स्वामित्व नहीं है।
     */
    VideoDetailsDTO getLiveStreamRecordById(Long videoId, User currentUser);

    /**
     * वर्तमान प्रमाणीकृत उपयोगकर्ता के स्वामित्व वाले सभी लाइव स्ट्रीम रिकॉर्ड की सूची प्राप्त करता है।
     * @param currentUser प्रमाणीकृत उपयोगकर्ता।
     * @return उपयोगकर्ता के स्वामित्व वाले रिकॉर्ड के लिए VideoDetailsDTOs की सूची।
     */
    List<VideoDetailsDTO> getMyLiveStreamRecords(User currentUser);

    /**
     * आंतरिक उपयोग (उदाहरण के लिए, फ़ाइल सेवा घटकों द्वारा) के लिए उसकी आईडी द्वारा वास्तविक न्यूनतम Video इकाई प्राप्त करता है।
     * यह विधि स्वामित्व/ऑथराइज़ेशन चेक नहीं करती है; यह माना जाता है कि इसे उच्च परत पर हैंडल किया जाता है।
     * @param videoId लाइव स्ट्रीम रिकॉर्ड की आईडी।
     * @return Video इकाई।
     * @throws FileNotFoundException यदि रिकॉर्ड नहीं मिलता है।
     */
    Video findLiveStreamEntityById(Long videoId) throws FileNotFoundException;

    /**
     * ऑथराइज़ेशन चेक के साथ उसकी आईडी द्वारा वास्तविक न्यूनतम Video इकाई प्राप्त करता है।
     * यह आंतरिक सेवा कॉल के लिए है जहाँ स्वामित्व को सत्यापित किया जाना चाहिए।
     * @param videoId लाइव स्ट्रीम रिकॉर्ड की आईडी।
     * @param user इकाई का अनुरोध करने वाला उपयोगकर्ता।
     * @return Video इकाई।
     * @throws FileNotFoundException यदि रिकॉर्ड नहीं मिलता है।
     * @throws SecurityException यदि उपयोगकर्ता अधिकृत नहीं है।
     */
    Video getActualLiveStreamEntity(Long videoId, User user);

    /**
     * रिकॉर्ड की गई .webm फ़ाइल को स्ट्रीम करने के लिए एक Resource तैयार करता है।
     * इसमें स्वामित्व/ऑथराइज़ेशन चेक शामिल हैं।
     * @param recordId स्ट्रीम किए जाने वाले रिकॉर्ड की आईडी।
     * @param currentUser प्रमाणीकृत उपयोगकर्ता।
     * @return .webm फ़ाइल के लिए Resource।
     * @throws FileNotFoundException यदि फ़ाइल नहीं मिलती है या उपयोगकर्ता के पास उसका स्वामित्व नहीं है।
     * @throws SecurityException यदि उपयोगकर्ता अधिकृत नहीं है।
     * @throws IOException यदि फ़ाइल को लोड करने में कोई त्रुटि आती है।
     */
    Resource prepareWebmStream(Long recordId, User currentUser) throws FileNotFoundException, SecurityException, IOException;

   
}