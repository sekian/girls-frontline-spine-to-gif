// Sekian#6855

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
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

public class CreateChibiFrames extends ApplicationAdapter {
    SkeletonRenderer renderer;
    AnimationState state;
    float fps = 30.0f;
    PolygonSpriteBatch batch;
    ThreadPoolExecutor pool;
    @Override
    public void create () {
        long start2 = System.currentTimeMillis();
        int nThreads = Runtime.getRuntime().availableProcessors();
        pool = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        batch = new PolygonSpriteBatch();
        renderer = new SkeletonRenderer();
        renderer.setPremultipliedAlpha(true);
        FileHandle dirHandle = Gdx.files.absolute("./");        
        FileHandle[] dir1 = dirHandle.list(".skel");
        FileHandle[] dir2 = dirHandle.list(".skel.txt");
        FileHandle[] dir = new FileHandle[dir1.length + dir2.length];
        System.arraycopy(dir1, 0, dir, 0, dir1.length);
        System.arraycopy(dir2, 0, dir, dir1.length, dir2.length);
        File file = new File("_out");
        file.mkdirs();
        for  (FileHandle skeletonFile: dir) {
            long start = System.currentTimeMillis();
            SkeletonData skeletonData = loadSkeleton(skeletonFile);
            String skeletonName = skeletonFile.nameWithoutExtension();
            if (skeletonName.endsWith(".skel")) skeletonName = skeletonName.substring(0, skeletonName.length() - 5);
            System.out.print(skeletonName+" ");            
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream("_out/" + skeletonName + ".zip");
            } catch (FileNotFoundException e1) {e1.printStackTrace();}
            BufferedOutputStream bout = new BufferedOutputStream(fout);
            ZipOutputStream zout = new ZipOutputStream(bout);  
            for (Animation animation : skeletonData.getAnimations()) {
                Vector2 min = new Vector2(0,0);
                Vector2 max = new Vector2(0,0);
                Vector2 pos = new Vector2(0,0);
                getAnimationBounds(min, max, skeletonData, animation.getName());
                pos.x = Math.abs(min.x); 
                pos.y = Math.abs(min.y); 
                max.x += Math.abs(min.x);
                max.y += Math.abs(min.y);
                min.x = 0;
                min.y = 0;
                try {saveAnimation(min, max, pos, skeletonData, animation.getName(), zout);} 
                catch (Exception e) {e.printStackTrace();}      
            }
            try {zout.close();
                bout.close();
                fout.close();} 
            catch (IOException e) { e.printStackTrace(); }            
            long end = System.currentTimeMillis();
            System.out.print((end - start)/1000.0 + "s\n");
        }
        long end2 = System.currentTimeMillis();
        System.out.println((end2 - start2)/1000.0 + "s");
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
    public void getAnimationBounds(Vector2 min, Vector2 max, SkeletonData skeletonData, String animation) {
        float minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        Skeleton skeleton = new Skeleton(skeletonData);
        skeleton.setToSetupPose();
        skeleton = new Skeleton(skeleton);
        skeleton.updateWorldTransform();
        AnimationState state = new AnimationState(new AnimationStateData(skeleton.getData()));
        state.getData().setDefaultMix(0.0f);
        state.setAnimation(0, animation, false); 
        while (!state.getCurrent(0).isComplete()) {              
            float deltaFake = (1.0f/fps);
            skeleton.update(deltaFake);
            state.update(deltaFake);
            state.apply(skeleton);
            skeleton.setPosition(0, 0);
            skeleton.updateWorldTransform();
            getBounds(min, max, skeleton);    
            maxX = Math.max(maxX, max.x+min.x); //We want the absolute max value (not the distance)
            maxY = Math.max(maxY, max.y+min.y); //so we cancel the substracted min returned from getBounds
            minX = Math.min(minX, min.x);
            minY = Math.min(minY, min.y);
        }
        min.set(minX, minY);
        max.set(maxX, maxY);
        //System.out.println(minX +" "+ minY);
        //System.out.println(maxX +" "+ maxY);
    }
    public void saveAnimation (Vector2 min, Vector2 max, Vector2 pos, SkeletonData skeletonData, String animation, ZipOutputStream zout) throws Exception {
        AnimationState state = new AnimationState(new AnimationStateData(skeletonData));
        renderer.setPremultipliedAlpha(true);
        Skeleton skeleton = new Skeleton(skeletonData);
        skeleton.setToSetupPose();
        skeleton = new Skeleton(skeleton);
        skeleton.updateWorldTransform();
        state.getData().setDefaultMix(0.0f);
        state.setAnimation(0, animation, false);
        int numFrame = 0;      
        while (!state.getCurrent(0).isComplete()) {
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            float deltaFake = (1.0f/fps);
            state.update(deltaFake);
            state.apply(skeleton);            
            skeleton.setPosition(pos.x, pos.y);
            skeleton.updateWorldTransform();                
            batch.begin();
            renderer.draw(batch, skeleton);
            batch.end();
            int minX = (int)Math.floor(min.x);
            int minY = (int)Math.floor(min.y);
            int maxX = (int)Math.ceil(max.x)-minX;
            int maxY = (int)Math.ceil(max.y)-minY;
            byte[] pixels = ScreenUtils.getFrameBufferPixels(minX, minY, maxX, maxY, true);
            BufferedImage image = new BufferedImage(maxX, maxY, BufferedImage.TYPE_INT_ARGB);    
            for (int x = 0; x < maxX; x++) {
                for (int y = 0; y < maxY; y++) {
                    int i = (x + (maxX * y)) * 4;
                    int r = pixels[i + 0] & 0xFF;
                    int g = pixels[i + 1] & 0xFF;
                    int b = pixels[i + 2] & 0xFF;
                    int a = pixels[i + 3] & 0xFF;
                    image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            String filePath = String.format(state.getCurrent(0)+"/%04d.gif", numFrame);
            ZipEntry entry = new ZipEntry(filePath);
            zout.putNextEntry(entry);
            ImageIO.write(image, "gif", zout);
            zout.closeEntry();
            ++numFrame;
        }
    }
    public static void main(String [] args) { 
        CreateChibiFrames anim = new CreateChibiFrames();
        if (args.length >= 1) anim.fps = Float.parseFloat(args[0]);
        System.out.println(anim.fps);
        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.width = (int)(640);
        config.height = (int)(360);
        new LwjglApplication(anim, config);        
    }
}
