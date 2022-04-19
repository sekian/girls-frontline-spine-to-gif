// Sekian#6855

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.PixmapIO.PNG;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
//import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.SkinnedMeshAttachment;
import com.github.dragon66.AnimatedGIFWriter;

public class CreateChibiFrames extends ApplicationAdapter {
    public enum Format {
        PNG, PNG8, PNG32, GIF
    }
    float delta = 1/30.0f;
    ThreadPoolExecutor pool;
    CountDownLatch latch;
    Format format = Format.PNG;    
    public void create () {
        long start = System.currentTimeMillis();
        latch = new CountDownLatch(Integer.MAX_VALUE);
        FileHandle dirHandle = Gdx.files.absolute("./");
        FileHandle[] a = dirHandle.list(".skel");
        FileHandle[] b = dirHandle.list(".skel.txt");
        List<FileHandle> directory = new ArrayList<FileHandle>(a.length + b.length);
        directory.addAll(Arrays.asList(a));
        directory.addAll(Arrays.asList(b));
        File file = new File("_out"); file.mkdirs();
        int nThreads = Runtime.getRuntime().availableProcessors() - 1;
        nThreads = Math.max(nThreads, 1);
        pool = new ThreadPoolExecutor(nThreads, Integer.MAX_VALUE, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        System.out.printf("%d files found\nProcessing starts...\n", directory.size());
        for (int i = 0; i < directory.size(); ++i) {
        	long start2 = System.currentTimeMillis();
            FileHandle skeletonFile = directory.get(i);
            SkeletonData skeletonData = null;
            try {
                skeletonData = loadSkeleton(skeletonFile);
            } catch (Exception e) {
                System.out.printf("%4d/%-6d %-5ss    FAILED LOADING %s\n", i + 1, directory.size(),0.0f, skeletonFile.name());
                //e.printStackTrace();
                continue;
            }
            String skeletonName = skeletonFile.nameWithoutExtension();
            if (skeletonName.endsWith(".skel")) skeletonName = skeletonName.substring(0, skeletonName.length() - 5);
            file = new File("_out/" + skeletonName); file.mkdirs();
            for (Animation animation : skeletonData.getAnimations()) {
                Vector2 min = new Vector2(0,0);
                Vector2 max = new Vector2(0,0);
                Vector2 pos = new Vector2(0,0);
                getAnimationBounds(min, max, skeletonData, animation);
                pos.x = Math.abs(min.x);
                pos.y = Math.abs(min.y);
                max.x += Math.abs(min.x);
                max.y += Math.abs(min.y);
                min.x = 0;
                min.y = 0;
                try {
                    int size = pool.getQueue().size();
                    if (size > nThreads) {
                        latch = new CountDownLatch(size - nThreads);
                        latch.await(8, TimeUnit.SECONDS);
                    }
                    saveAnimation(min, max, pos, skeletonData, animation);
                }   catch (Exception e) {e.printStackTrace();}      
            }
            long end2 = System.currentTimeMillis();
            System.out.printf("%4d/%-6d %-5ss    %s\n", i + 1, directory.size(),(end2 - start2)/1000.0, skeletonName);
        }
        System.out.println("Waiting for active threads to end...");
        pool.shutdown();        
        try {
            while(!pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES)) {
                latch = new CountDownLatch(pool.getActiveCount());
                latch.await(8, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {e.printStackTrace();}
        long end = System.currentTimeMillis();
        System.out.println("Uptime " + (end - start)/1000.0 + "s");
        Gdx.app.exit();
    }
    public SkeletonData loadSkeleton (FileHandle skeletonFile) {
        if (skeletonFile == null) return null;
        String atlasFileName = skeletonFile.nameWithoutExtension();
        if (atlasFileName.endsWith(".skel"))
            atlasFileName = atlasFileName.substring(0, atlasFileName.length() - 5);
        FileHandle atlasFile = skeletonFile.sibling(atlasFileName + ".atlas");
        if (!atlasFile.exists()) 
            atlasFile = skeletonFile.sibling(atlasFileName + ".atlas.txt");        
        if (!atlasFile.exists() && atlasFileName.charAt(0) == 'R')
            atlasFile = skeletonFile.sibling(atlasFileName.substring(1) + ".atlas");            
        if (!atlasFile.exists() && atlasFileName.charAt(0) == 'R') 
            atlasFile = skeletonFile.sibling(atlasFileName.substring(1) + ".atlas.txt");        
        if (!atlasFile.exists() && atlasFileName.charAt(0) == 'R')
            atlasFile = skeletonFile.sibling(atlasFileName.substring(1) + ".atlas");    
        if (!atlasFile.exists() && atlasFileName.charAt(0) == 'R') 
            atlasFile = skeletonFile.sibling(atlasFileName.substring(1) + ".atlas.txt");
        if (!atlasFile.exists() && atlasFileName.endsWith("_NPC"))          
            atlasFile = skeletonFile.sibling(atlasFileName.substring(0, atlasFileName.length() - 3)+ "Boss" + ".atlas");
        if (!atlasFile.exists() && atlasFileName.endsWith("_NPC"))
            atlasFile = skeletonFile.sibling(atlasFileName.substring(0, atlasFileName.length() - 3)+ "Boss" + ".atlas.txt");
        if (!atlasFile.exists() && atlasFileName.contains("_"))
            atlasFile = skeletonFile.sibling(atlasFileName.substring(0, atlasFileName.lastIndexOf('_'))+ ".atlas");
        if (!atlasFile.exists() && atlasFileName.contains("_"))
            atlasFile = skeletonFile.sibling(atlasFileName.substring(0, atlasFileName.lastIndexOf('_'))+ ".atlas.txt");
        if (!atlasFile.exists() && (atlasFileName.endsWith("A") || atlasFileName.endsWith("B") || atlasFileName.endsWith("C")))
            atlasFile = skeletonFile.sibling(atlasFileName.substring(1, atlasFileName.length() - 1)+ ".atlas");
        if (!atlasFile.exists() && (atlasFileName.endsWith("A") || atlasFileName.endsWith("B") || atlasFileName.endsWith("C")))
            atlasFile = skeletonFile.sibling(atlasFileName.substring(1, atlasFileName.length() - 1)+ ".atlas.txt");        
        TextureAtlasData data = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
        TextureAtlas atlas = new TextureAtlas(data);        
        SkeletonBinary binary = new SkeletonBinary(atlas);
        SkeletonData skeletonData = binary.readSkeletonData(skeletonFile);
        return skeletonData;
    }
    // Modified getBounds to calculate correct AABB size
    public void getBounds (Vector2 offset, Vector2 size, Skeleton skeleton) {
        Array<Slot> drawOrder = skeleton.getDrawOrder();
        float minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0, n = drawOrder.size; i < n; i++) {
            Slot slot = drawOrder.get(i);
            float[] vertices1 = null;
            float[] vertices2 = null;
            float[] vertices3 = null;
            Attachment attachment = slot.getAttachment();
            if (attachment instanceof RegionAttachment) {
                RegionAttachment region = (RegionAttachment)attachment;
                region.updateWorldVertices(slot, false);
                vertices1 = region.getWorldVertices();
            } else if (attachment instanceof MeshAttachment) {
                MeshAttachment mesh = (MeshAttachment)attachment;
                mesh.updateWorldVertices(slot, true);
                vertices2 = mesh.getWorldVertices();
            } else if (attachment instanceof SkinnedMeshAttachment) {
                SkinnedMeshAttachment mesh = (SkinnedMeshAttachment)attachment;
                mesh.updateWorldVertices(slot, true);
                vertices3 = mesh.getWorldVertices();
            }
            if (vertices1 != null) {
                for (int ii = 0, nn = vertices1.length; ii < nn; ii += 5) {
                    float x = vertices1[ii], y = vertices1[ii + 1];
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
            if (vertices2 != null) {
                for (int ii = 0, nn = vertices2.length; ii < nn; ii += 5) {
                    float x = vertices2[ii], y = vertices2[ii + 1];
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
            if (vertices3 != null) {
                for (int ii = 0, nn = vertices3.length; ii < nn; ii += 5) {
                    float x = vertices3[ii], y = vertices3[ii + 1];
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        offset.set(minX, minY);
        size.set(maxX - minX, maxY - minY);
    }    
    //max and min world coordinates for all the animation
    public void getAnimationBounds(Vector2 min, Vector2 max, SkeletonData skeletonData, Animation animation) {
        float minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        Skeleton skeleton = new Skeleton(skeletonData);
        skeleton.setToSetupPose();
        skeleton = new Skeleton(skeleton);
        skeleton.updateWorldTransform();
        AnimationState state = new AnimationState(new AnimationStateData(skeleton.getData()));
        state.getData().setDefaultMix(0.0f);
        state.setAnimation(0, animation, false);
        while (!state.getCurrent(0).isComplete()) {
            skeleton.update(delta);
            state.update(delta);
            state.apply(skeleton);
            skeleton.setPosition(0, 0);
            skeleton.updateWorldTransform();
            //getBounds(min, max, skeleton);
            skeleton.getBounds(min, max);
            maxX = Math.max(maxX, max.x+min.x); //Cancel the substracted min returned from getBounds
            maxY = Math.max(maxY, max.y+min.y); //because we want the absolute max value (not the distance)
            minX = Math.min(minX, min.x);
            minY = Math.min(minY, min.y);
        }
        min.set(minX, minY);
        max.set(maxX, maxY);
        //System.out.println(min +" "+ min);
    }
    public void saveAnimation(Vector2 min, Vector2 max, Vector2 pos, SkeletonData skeletonData, Animation animation) throws Exception {
        AnimationState state = new AnimationState(new AnimationStateData(skeletonData));
        Skeleton skeleton = new Skeleton(skeletonData);
        skeleton.setToSetupPose();
        skeleton.updateWorldTransform();
        state.getData().setDefaultMix(0.0f);
        state.setAnimation(0, animation, false);        
        skeleton.setPosition(pos.x, pos.y);
        int minX = (int)Math.floor(min.x);
        int minY = (int)Math.floor(min.y);
        int maxX = (int)Math.ceil(max.x)-minX;
        int maxY = (int)Math.ceil(max.y)-minY;
        //System.out.println(min +" "+ pos + " " + max);
        if (Gdx.graphics.getWidth() < maxX || Gdx.graphics.getHeight() < maxY) {
        	int new_sizeX = Math.max(maxX, Gdx.graphics.getWidth());
        	int new_sizeY = Math.max(maxY, Gdx.graphics.getHeight());
        	Gdx.graphics.setWindowedMode(new_sizeX, new_sizeY);
        }
        int size = (int)(animation.getDuration()/delta) + 2;
        List<byte[]> frames = new ArrayList<byte[]>(size);
        SkeletonRenderer renderer = new SkeletonRenderer();
        PolygonSpriteBatch batch = new PolygonSpriteBatch();
        renderer.setPremultipliedAlpha(true);
        while (!state.getCurrent(0).isComplete()) {
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            state.update(delta);
            state.apply(skeleton);
            skeleton.updateWorldTransform();
            batch.begin();
            renderer.draw(batch, skeleton);
            batch.end();
            byte[] pixels = ScreenUtils.getFrameBufferPixels(minX, minY, maxX, maxY, false);            
            frames.add(pixels);
        }
        pool.execute(new Runnable() {
            public void run() {
                try {
                    switch (format) {
                    case PNG:
                    case PNG32: 
                        saveWorkLossless(frames, animation.getName(), skeletonData.getName(), maxX, maxY);
                        break;
                    case GIF:
                    case PNG8:
                        saveWorkLossy(frames, animation.getName(), skeletonData.getName(), maxX, maxY);
                        break;                      
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }
        });  
    }
    public void saveWorkLossy(List<byte[]> frames, String animationName, String skeletonName, int maxX, int maxY) throws Exception {
        if (skeletonName.endsWith(".skel")) 
            skeletonName = skeletonName.substring(0, skeletonName.length() - 5);        
        FileOutputStream fout = new FileOutputStream("_out/" + skeletonName +"/"+ animationName + ".zip");
        BufferedOutputStream bout = new BufferedOutputStream(fout);
        ZipOutputStream zout = new ZipOutputStream(bout);
        zout.setLevel(Deflater.NO_COMPRESSION);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int ii = 0; ii < frames.size(); ++ii) {
            byte[] pixels = frames.get(ii);
            BufferedImage image = new BufferedImage(maxX, maxY, BufferedImage.TYPE_INT_ARGB);    
            for (int x = 0; x < maxX; x++) {
                for (int y = 0; y < maxY; y++) {
                    int i = (x + (maxX * y)) * 4;
                    int r = pixels[i + 0] & 0xFF;
                    int g = pixels[i + 1] & 0xFF;
                    int b = pixels[i + 2] & 0xFF;
                    int a = pixels[i + 3] & 0xFF;
                    image.setRGB(x, maxY - y - 1, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            pixels = null;
            frames.set(ii, null);
            AnimatedGIFWriter writer = new AnimatedGIFWriter(false);
            if (format == Format.GIF) {
                String filePath = String.format("%04d.gif", ii);
                zout.putNextEntry(new ZipEntry(filePath));
                writer.prepareForWrite(zout, maxX, maxY);
                writer.writeFrame(zout, image, Math.round(1000*delta));                
            }
            else { //PNG8
                String filePath = String.format("%04d.png", ii);
                zout.putNextEntry(new ZipEntry(filePath));
                writer.prepareForWrite(baos, maxX, maxY);
                writer.writeFrame(baos, image, Math.round(1000*delta));
                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                BufferedImage im1 = ImageIO.read(bais);
                ImageIO.write(im1, "png", zout);
                bais.close();
            }
            zout.closeEntry(); 
            image.flush();
            image = null;
        }
        //frames.clear();
        frames = null;
        zout.close();
        bout.close();
        fout.close();
    }
    public void saveWorkLossless(List<byte[]> frames, String animationName, String skeletonName, int maxX, int maxY) throws IOException {
        if (skeletonName.endsWith(".skel")) 
            skeletonName = skeletonName.substring(0, skeletonName.length() - 5);        
        FileOutputStream fout = new FileOutputStream("_out/" + skeletonName +"/"+ animationName + ".zip");
        BufferedOutputStream bout = new BufferedOutputStream(fout);
        ZipOutputStream zout = new ZipOutputStream(bout);
        zout.setLevel(Deflater.NO_COMPRESSION);
        PNG encoder = new PixmapIO.PNG();
        encoder.setFlipY(true);
        //encoder.setCompression(Deflater.BEST_SPEED);
        for (int ii = 0; ii < frames.size(); ++ii) {
            byte[] pixels = frames.get(ii);
            Pixmap pixmap = new Pixmap(maxX, maxY, Pixmap.Format.RGBA8888);
            BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
            pixels = null;
            frames.set(ii, null);
            String filePath = String.format("%04d.png", ii);
            zout.putNextEntry(new ZipEntry(filePath));
            encoder.write(zout, pixmap);
            zout.closeEntry(); 
            pixmap.dispose();
        }
        //frames.clear();
        frames = null;
        zout.close();
        bout.close();
        fout.close();
    }
    public static void main(String [] args) {
        CreateChibiFrames anim = new CreateChibiFrames();
        for (int i = 0; i < args.length; i += 2) {
            switch (args[i]) {
                case "-framerate":
                    anim.delta = 1/Float.parseFloat(args[i + 1]);
                    break;
                case "-format":
                    anim.format = Format.valueOf(args[i + 1].toUpperCase());
                    break;
            }
        }
        System.out.println("Framerate = " + Math.round(1/anim.delta) + "\nFormat = "+anim.format);
        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.width = (int)(0);
        config.height = (int)(0);
        new LwjglApplication(anim, config);
    }
}
