# sdk_uvc_camera

* UVCCamera call step
```text
You must call the method of '*' mark method at complete the use camera process
1. *UVCCamera camera = new UVCCamera(block);                                               
    camera.setPreviewRotate(UVCCamera.PREVIEW_ROTATE.ROTATE_90);                            
    camera.setPreviewFlip(UVCCamera.PREVIEW_FLIP.FLIP_H);                                   
 2. *camera.setPreviewSize();                                                               
    camera.setPreviewDisplay(surface);                                                      
    camera.setFrameCallback();                                                              
 3. *camera.startPreview();                                                                 
 4. *camera.stopPreview();                                                                  
 5. *camera.destroy();                                                                      
    camera = null; 
```