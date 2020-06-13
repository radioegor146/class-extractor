package by.radioegor146.classextractor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.zip.ZipOutputStream;

public class Main {

    private static final int CLASS_SIZE_LIMIT = 256 * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java -jar class-extractor.jar <mode (o - try to read and rename with asm, w - read and write with asm, s - skip unreadable classes)> <path to binary file> <output path>");
            return;
        }

        boolean rename = args[0].contains("o");
        boolean rewrite = args[0].contains("w");
        boolean skip = args[0].contains("s");

        int currentUnknownClassId = 0;

        Set<String> writtenClasses = new HashSet<>();

        System.err.println(Paths.get("").toAbsolutePath().toString());

        try (InputStream stream = new FileInputStream(args[1]);
             InputStreamBuffer inputStreamBuffer = new InputStreamBuffer(stream);
             ZipOutputStream outputFile = new ZipOutputStream(new FileOutputStream(args[2]))) {
            while (inputStreamBuffer.available() > 3) {
                byte[] header = inputStreamBuffer.peekData(4);
                if ((header[0] & 0xFF) == 0xCA && (header[1] & 0xFF) == 0xFE && (header[2] & 0xFF) == 0xBA && (header[3] & 0xFF) == 0xBE) {
                    System.out.println("Found possible class! Analyzing...");
                    byte[] classData = inputStreamBuffer.peekData(Math.min(CLASS_SIZE_LIMIT, inputStreamBuffer.available()));

                    currentUnknownClassId++;
                    String newClassName = String.format("unknown/Class%d", currentUnknownClassId);

                    if (skip) {
                        try {
                            ClassReader reader = new ClassReader(classData);
                            ClassNode node = new ClassNode(Opcodes.ASM7);
                            reader.accept(node, 0);
                        } catch (Exception e) {
                            System.out.println("Not a class. Skipping...");
                            continue;
                        }
                    }

                    if (rename) {
                        try {
                            ClassReader reader = new ClassReader(classData);
                            ClassNode node = new ClassNode(Opcodes.ASM7);
                            reader.accept(node, 0);
                            newClassName = reader.getClassName();
                            int subIndex = 0;
                            while (writtenClasses.contains(newClassName)) {
                                newClassName = reader.getClassName() + subIndex;
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    if (rewrite) {
                        try {
                            ClassReader reader = new ClassReader(classData);
                            ClassNode node = new ClassNode(Opcodes.ASM7);
                            reader.accept(node, 0);
                            ClassWriter writer = new ClassWriter(Opcodes.ASM7);
                            node.accept(writer);
                            classData = writer.toByteArray();
                        } catch (Exception ignored) {
                        }
                    }

                    System.out.println(String.format("Class of size %d saved as %s", classData.length, newClassName));

                    writtenClasses.add(newClassName);
                    outputFile.putNextEntry(new JarEntry(newClassName + ".class"));
                    outputFile.write(classData);
                    outputFile.closeEntry();
                }

                inputStreamBuffer.moveForward(1);
            }
        }
    }

    private static class InputStreamBuffer implements Closeable {

        private final InputStream inputStream;

        private final ArrayDeque<byte[]> buffers = new ArrayDeque<>();
        private int firstBufferPosition = 0;

        public InputStreamBuffer(InputStream inputStream) {
            this.inputStream = inputStream;
            buffers.add(new byte[0]);
        }

        public void moveForward(int bytes) throws IOException {
            ensureBufferAvailability(bytes);
            while (bytes > 0) {
                firstBufferPosition += bytes;

                byte[] currentBuffer = buffers.peek();
                if (currentBuffer == null) {
                    throw new UnsupportedOperationException();
                }

                if (firstBufferPosition >= currentBuffer.length) {
                    buffers.poll();
                    firstBufferPosition = 0;
                    bytes -= currentBuffer.length;
                } else {
                    break;
                }
            }
        }

        private int getStoredSize() {
            int currentSize = -firstBufferPosition;

            for (byte[] buffer : buffers) {
                currentSize += buffer.length;
            }

            return currentSize;
        }

        private void ensureBufferAvailability(int size) throws IOException {
            int currentSize = getStoredSize();

            while (currentSize < size) {
                byte[] buffer = new byte[4096];
                int readSize;
                if ((readSize = inputStream.read(buffer)) <= 0) {
                    throw new EOFException();
                }
                currentSize += readSize;
                buffers.offer(Arrays.copyOf(buffer, readSize));
            }
        }

        public byte[] peekData(int size) throws IOException {
            ensureBufferAvailability(size);
            byte[] result = new byte[size];
            int currentPointer = 0;
            boolean isFirstBuffer = true;
            for (byte[] buffer : buffers) {
                if (currentPointer >= size) {
                    return result;
                }

                int bytesToRead = Math.min(buffer.length - (isFirstBuffer ? firstBufferPosition : 0), size - currentPointer);
                System.arraycopy(buffer, isFirstBuffer ? firstBufferPosition : 0, result, currentPointer, bytesToRead);
                currentPointer += bytesToRead;

                isFirstBuffer = false;
            }
            return result;
        }

        public int available() throws IOException {
            return getStoredSize() + inputStream.available();
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
