// Copyright 2015 Sebastian Kuerten
//
// This file is part of osm4j.
//
// osm4j is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// osm4j is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with osm4j. If not, see <http://www.gnu.org/licenses/>.

package de.topobyte.osm4j.pbf.util;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import com.google.protobuf.ByteString;

import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.impl.Bounds;
import de.topobyte.osm4j.pbf.Compression;
import de.topobyte.osm4j.pbf.Constants;
import de.topobyte.osm4j.pbf.protobuf.Fileformat;
import de.topobyte.osm4j.pbf.protobuf.Osmformat;
import de.topobyte.osm4j.pbf.protobuf.Osmformat.HeaderBBox;

public class PbfUtil
{

	public static Osmformat.HeaderBlock createHeader(String writingProgram,
			boolean requiresDense, OsmBounds bound)
	{
		Osmformat.HeaderBlock.Builder headerblock = Osmformat.HeaderBlock
				.newBuilder();

		if (bound != null) {
			Osmformat.HeaderBBox.Builder bbox = Osmformat.HeaderBBox
					.newBuilder();
			bbox.setLeft(bboxDegreesToLong(bound.getLeft()));
			bbox.setBottom(bboxDegreesToLong(bound.getBottom()));
			bbox.setRight(bboxDegreesToLong(bound.getRight()));
			bbox.setTop(bboxDegreesToLong(bound.getTop()));
			headerblock.setBbox(bbox);
		}

		headerblock.setWritingprogram(writingProgram);
		headerblock.addRequiredFeatures(Constants.FEATURE_SCHEMA_0_6);
		if (requiresDense) {
			headerblock.addRequiredFeatures(Constants.FEATURE_DENSE_NODES);
		}
		return headerblock.build();
	}

	private static long bboxDegreesToLong(double value)
	{
		return (long) (value / .000000001);
	}

	public static double bboxLongToDegrees(long value)
	{
		return value * .000000001;
	}

	public static BlobHeader parseHeader(DataInput input) throws IOException
	{
		int lengthHeader = input.readInt();
		try {
			return parseHeader(input, lengthHeader);
		} catch (EOFException e) {
			throw new IOException("Unable to parse blob header", e);
		}
	}

	public static BlobHeader parseHeader(DataInput input, int lengthHeader)
			throws IOException
	{
		byte buf[] = new byte[lengthHeader];
		input.readFully(buf);

		Fileformat.BlobHeader header = Fileformat.BlobHeader.parseFrom(buf);
		BlobHeader h = new BlobHeader(header.getType(), header.getDatasize(),
				header.getIndexdata());

		return h;
	}

	public static Fileformat.Blob parseBlock(DataInput data, int lengthData)
			throws IOException
	{
		byte buf[] = new byte[lengthData];
		data.readFully(buf);

		Fileformat.Blob blob = Fileformat.Blob.parseFrom(buf);
		return blob;
	}

	private static LZ4FastDecompressor lz4Decompressor = null;

	private static void initLz4()
	{
		if (lz4Decompressor == null) {
			LZ4Factory factory = LZ4Factory.fastestInstance();
			lz4Decompressor = factory.fastDecompressor();
		}
	}

	public static BlockData getBlockData(Fileformat.Blob blob)
			throws IOException
	{
		ByteString blobData;
		Compression compression;
		if (blob.hasRaw()) {
			compression = Compression.NONE;
			blobData = blob.getRaw();
		} else if (blob.hasZlibData()) {
			compression = Compression.DEFLATE;
			byte uncompressed[] = new byte[blob.getRawSize()];

			Inflater decompresser = new Inflater();
			decompresser.setInput(blob.getZlibData().toByteArray());
			try {
				decompresser.inflate(uncompressed);
			} catch (DataFormatException e) {
				throw new IOException("Error while decompressing gzipped data",
						e);
			}
			decompresser.end();

			blobData = ByteString.copyFrom(uncompressed);
		} else if (blob.hasLz4Data()) {
			compression = Compression.LZ4;
			byte uncompressed[] = new byte[blob.getRawSize()];

			initLz4();
			lz4Decompressor.decompress(blob.getLz4Data().toByteArray(), 0,
					uncompressed, 0, blob.getRawSize());

			blobData = ByteString.copyFrom(uncompressed);
		} else {
			throw new IOException("Encountered block without data");
		}

		return new BlockData(blobData, compression);
	}

	public static OsmBounds bounds(HeaderBBox bbox)
	{
		double left = PbfUtil.bboxLongToDegrees(bbox.getLeft());
		double right = PbfUtil.bboxLongToDegrees(bbox.getRight());
		double top = PbfUtil.bboxLongToDegrees(bbox.getTop());
		double bottom = PbfUtil.bboxLongToDegrees(bbox.getBottom());
		return new Bounds(left, right, top, bottom);
	}

}
