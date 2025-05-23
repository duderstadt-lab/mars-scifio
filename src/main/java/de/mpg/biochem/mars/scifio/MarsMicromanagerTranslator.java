/*
 * #%L
 * SCIFIO adapted Mars Micromanager format and translator.
 * %%
 * Copyright (C) 2020 - 2025 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.scifio;

import io.scif.ome.translators.*;

import io.scif.FormatException;
import io.scif.common.DateTools;
import io.scif.ome.OMEMetadata;
import io.scif.ome.services.OMEMetadataService;
import org.scijava.io.location.Location;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scijava.Priority;
import org.scijava.io.handle.DataHandleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.mars.scifio.MarsMicromanagerFormat.Metadata;
import de.mpg.biochem.mars.scifio.MarsMicromanagerFormat.Position;
import loci.formats.ome.OMEXMLMetadata;
import ome.units.UNITS;
import ome.units.quantity.ElectricPotential;
import ome.units.quantity.Length;
import ome.units.quantity.Temperature;
import ome.units.quantity.Time;
import ome.xml.model.MapPair;
import ome.xml.model.primitives.Timestamp;

/**
 * Container class for translators between OME and Micromanager formats.
 *
 * @author Mark Hiner hinerm at gmail.com
 * 
 * Small edits so that all fields per plane are translated into the OME record. Edits are Tagged with DROP-IN. 
 * 
 * @author Karl Duderstadt
 */
public class MarsMicromanagerTranslator {

	/**
	 * Translator class from {@link OMEMetadata} to
	 * {@link io.scif.formats.MicromanagerFormat.Metadata}.
	 * <p>
	 * NB: Plugin priority is set to high to be selected over the base
	 * {@link io.scif.Metadata} translator.
	 * </p>
	 * 
	 * @author Mark Hiner
	 */
	@Plugin(type = FromOMETranslator.class, priority = Priority.HIGH)
	public static class MarsMicromanagerOMETranslator extends
		ToOMETranslator<MarsMicromanagerFormat.Metadata>
	{

		// -- Fields --

		@Parameter
		private OMEMetadataService omexmlMetadataService;

		@Parameter
		private DataHandleService dataHandleService;

		// -- Translator API --

		@Override
		public Class<? extends io.scif.Metadata> source() {
			return MarsMicromanagerFormat.Metadata.class;
		}

		@Override
		public Class<? extends io.scif.Metadata> dest() {
			return OMEMetadata.class;
		}

		@Override
		protected void translateFormatMetadata(
			final MarsMicromanagerFormat.Metadata source, final OMEMetadata dest)
		{
			try {
				populateMetadata(source, dest.getRoot());
			}
			catch (final FormatException | IOException e) {
				log().error(
					"Error populating Metadata store with Micromanager metadata", e);
			}
		}

		private void populateMetadata(final Metadata meta,
			final OMEXMLMetadata store) throws FormatException, IOException
		{
			final String instrumentID = //
				omexmlMetadataService.createLSID("Instrument", 0);
			store.setInstrumentID(instrumentID, 0);
			final List<Position> positions = meta.getPositions();
			
			for (int i = 0; i < positions.size(); i++) {
				final Position p = positions.get(i);
				if (p.time != null) {
					final String date = DateTools.formatDate(p.time, MarsMicromanagerFormat.Parser.DATE_FORMAT);
					
					if (date != null)
						store.setImageAcquisitionDate(new Timestamp(date), i);
				}
				
				if (p.UUID != null)
					store.setUUID(p.UUID);

				//if (positions.size() > 1) {
				//	final Location parent = p.metadataFile.parent();
				String imageName = (meta.get(i) != null) ? meta.get(i).getName() : p.metadataFile.parent().getName();	
				store.setImageName(imageName, i);
				//}

				store.setImageDescription(p.comment, i);

				// link Instrument and Image
				store.setImageInstrumentRef(instrumentID, i);

				for (int c = 0; c < p.channels.length; c++) {
					store.setChannelName(p.channels[c], i, c);
				}

				if (p.pixelSize != null && p.pixelSize > 0) {
					store.setPixelsPhysicalSizeX(//
						new Length(p.pixelSize, UNITS.MICROMETER), i);
					store.setPixelsPhysicalSizeY(//
						new Length(p.pixelSize, UNITS.MICROMETER), i);
				}
				//else {
				//	log().warn("Expected positive value for PhysicalSizeX; got " +
				//		p.pixelSize + ".");
				//}
				if (p.sliceThickness != null && p.sliceThickness > 0) {
					store.setPixelsPhysicalSizeZ(//
						new Length(p.sliceThickness, UNITS.MICROMETER), i);
				}
				//else {
				//	log().warn("Expected positive value for PhysicalSizeZ; got " +
				//		p.sliceThickness);
				//}
				
				//For some reason setImageID is squashed by scifio
				//so for now just add actual position index number
				//as annotation.
				store.setDoubleAnnotationValue((double)p.positionIndex, 0);
				store.setDoubleAnnotationID("ImageID", 0);

				int nextStamp = 0;
				for (int q = 0; q < meta.get(i).getPlaneCount(); q++) {
					if (p.exposureTime != null)
						store.setPlaneExposureTime(new Time(p.exposureTime, UNITS.SECOND), i,
							q);

					ArrayList<MapPair> planeParameterList = new ArrayList<MapPair>();

					//Check if the plane exists. If a sparse collection was performed some planes
					//may not have been collected.
					if (positions.get(i).hasPlane(meta, i, q)) {
						if (nextStamp < p.timestamps.length)
							store.setPlaneDeltaT(new Time(p.timestamps[nextStamp++],
									UNITS.SECOND), i, q);

						store.setPlaneTheC(p.getTheC(meta, i, q), i, q);
						store.setPlaneTheZ(p.getTheZ(meta, i, q), i, q);
						store.setPlaneTheT(p.getTheT(meta, i, q), i, q);

						Map<String, String> planeMetaTable = p.getPlaneMap(meta, i, q);

						for (String planeParameterKey : planeMetaTable.keySet())
							planeParameterList.add(new MapPair(planeParameterKey, planeMetaTable.get(planeParameterKey)));
					}

					store.setMapAnnotationValue(planeParameterList, q);
					store.setMapAnnotationID("MPlane-" + i + "-" + q, q);
					store.setPlaneAnnotationRef("MPlane-" + i + "-" + q, i, q, 0);
				}

				final String serialNumber = p.detectorID;
				p.detectorID = omexmlMetadataService.createLSID("Detector", 0, i);

				for (int c = 0; c < p.channels.length; c++) {
					store.setDetectorSettingsBinning(//
						omexmlMetadataService.getBinning(p.binning), i, c);
					store.setDetectorSettingsGain(new Double(p.gain), i, c);
					if (c < p.voltage.size()) {
						store.setDetectorSettingsVoltage(new ElectricPotential(p.voltage
							.get(c), UNITS.VOLT), i, c);
					}
					store.setDetectorSettingsID(p.detectorID, i, c);
				}

				store.setDetectorID(p.detectorID, 0, i);
				if (p.detectorModel != null) {
					store.setDetectorModel(p.detectorModel, 0, i);
				}

				if (serialNumber != null) {
					store.setDetectorSerialNumber(serialNumber, 0, i);
				}

				if (p.detectorManufacturer != null) {
					store.setDetectorManufacturer(p.detectorManufacturer, 0, i);
				}

				if (p.cameraMode == null) p.cameraMode = "Other";
				store.setDetectorType(//
					omexmlMetadataService.getDetectorType(p.cameraMode), 0, i);
				store.setImagingEnvironmentTemperature(//
					new Temperature(p.temperature, UNITS.CELSIUS), i);
			}
		}

	}
}
