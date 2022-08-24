package com.atguigu.gulimall.thirdpart;

//import org.junit.jupiter.api.Test;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;

@SpringBootTest
@RunWith(SpringRunner.class)
public class GulimallThirdpartApplicationTests {

    @Autowired
    private OSS ossClient;

    @Test
    public void testUpload(){
//        String endpoint = "oss-cn-qingdao.aliyuncs.com";
//        String accessKeyId = "LTAI5tNWcE25KmKP2PUHwQD3";
//        String accessKeySecret = "dK0xmM7GGhcrV7TW2qaII29Zllaeli";
        String bucketName = "gulimall---zch";
        String objectName = "test3.jpg";
        String filePath= "C:\\Users\\zch\\Desktop\\图片\\th.jpg";
        try {
            ossClient.putObject(bucketName, objectName, new File(filePath));
        } catch (OSSException oe) {
            oe.printStackTrace();
        } catch (ClientException ce) {
            ce.printStackTrace();
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
            System.out.println("success");
        }
    }

    @Test
    public void contextLoads() {
    }

}
