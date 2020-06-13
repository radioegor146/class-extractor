# class-extractor

.class file extractor from dumps or any binary files

Usage: ` java -jar class-extractor.jar <mode (o - try to read and rename with asm, w - read and write with asm, s - skip unreadable classes)> <path to binary file> <output path>`

Example: `java -jar class-extractor.jar ows input.bin output.jar`