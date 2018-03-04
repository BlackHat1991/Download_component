package com.electems.rmc.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.hibernate.exception.SQLGrammarException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.electems.rmc.dto.UserDataViewDTO;
import com.electems.rmc.model.AppConstant;
import com.electems.rmc.model.CommandsConfigurationVO;
import com.electems.rmc.model.Control;
import com.electems.rmc.model.ControlConfigVO;
import com.electems.rmc.model.Device;
import com.electems.rmc.repository.DeviceRepository;
import com.electems.rmc.service.AppConstantService;
import com.electems.rmc.service.ControlService;
import com.electems.rmc.service.DeviceService;
import com.electems.rmc.service.GroupService;

@Controller
@RequestMapping("/rest/device")
public class DeviceController extends AbstractController {

	@Inject
	DeviceService deviceService;

	@Inject
	private DeviceRepository deviceRepository;

	@Inject
	GroupService groupService;

	@Inject
	AppConstantService appConstantService;

	@Inject
	ControlService controlService;

	private int newPassWord = 0;

	private final static Map<String, String> claimFileMap = new HashMap<String, String>();

	@RequestMapping(value = "/deviceList", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Device>> getDeviceList(String userEmail) throws Exception {
		List<Device> list;
		List<String> userlist = userDataViewList(userEmail);
		list = deviceService.getDeviceListByFilter(userlist.get(0), userlist.get(1));
		return new ResponseEntity<List<Device>>(list, HttpStatus.OK);
	}

	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public ResponseEntity<Object> upload(@RequestPart("file") MultipartFile multipartFile,
			@RequestParam(value = "claimFileKey") String deviceFileKey) {
		String FileUploadPath = null;
		if (!multipartFile.isEmpty()) {
			try {
				List<AppConstant> filePath = appConstantService.getFileUploadPath();
				for (AppConstant appConstant : filePath) {
					if (appConstant.getCode().equalsIgnoreCase("fileUploadPath")) {
						FileUploadPath = appConstant.getValue();
					}
				}
				FileUploadPath = "/home/architect/project/iot/code/iot/Upload";

				String filename = multipartFile.getOriginalFilename();
				String directory = FileUploadPath;
				String filepath = Paths.get(directory, filename).toString();
				System.out.println("FIle Upload Path: " + filepath);
				BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(new File(filepath)));
				stream.write(multipartFile.getBytes());
				stream.close();
				claimFileMap.put(deviceFileKey, filename);
			} catch (Exception e) {
				return new ResponseEntity<Object>(HttpStatus.BAD_REQUEST);
			}
		} else {
			return new ResponseEntity<Object>(HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<Object>(HttpStatus.OK);

	}

	@RequestMapping(value = "/save", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Object>> importCSVFile(String type, Integer associatedID, String deviceFileKey,
			String userName) throws Exception {
		String deviceFileName = claimFileMap.get(deviceFileKey);
		List<Object> list = deviceService.importCSV(type, associatedID, deviceFileName, userName);
		return new ResponseEntity<List<Object>>(list, HttpStatus.OK);
	}

	public List<String> userDataViewList(String userEmail) {
		List<String> userDataViewList = new ArrayList<String>();
		UserDataViewDTO userDataviewDTO = USER_DATA.get(userEmail);
		String custlist = null, loclist = null;
		if (userDataviewDTO != null) {
			custlist = userDataviewDTO.getCustList();
			if (custlist != null) {
				custlist = custlist.replace("[", "");
				custlist = custlist.replace("]", "");
			}
			loclist = userDataviewDTO.getLocList();
			if (loclist != null) {
				loclist = loclist.replace("[", "");
				loclist = loclist.replace("]", "");
			}
		}
		userDataViewList.add(custlist);
		userDataViewList.add(loclist);
		return userDataViewList;
	}

	@RequestMapping(value = "/download", method = RequestMethod.GET)
	public void downloadFile(HttpServletResponse response, String filePath) throws IOException {

		File file = null;

		file = new File(filePath);

		if (!file.exists()) {
			String errorMessage = "Sorry. The file you are looking for does not exist";
			System.out.println(errorMessage);
			OutputStream outputStream = response.getOutputStream();
			outputStream.write(errorMessage.getBytes(Charset.forName("UTF-8")));
			outputStream.close();
			return;
		}

		String mimeType = URLConnection.guessContentTypeFromName(file.getName());
		if (mimeType == null) {
			System.out.println("mimetype is not detectable, will take default");
			mimeType = "application/octet-stream";
		}

		System.out.println("mimetype : " + mimeType);

		response.setContentType(mimeType);

		/*
		 * "Content-Disposition : inline" will show viewable types [like
		 * images/text/pdf/anything viewable by browser] right on browser while
		 * others(zip e.g) will be directly downloaded [may provide save as
		 * popup, based on your browser setting.]
		 */
		response.setHeader("Content-Disposition", String.format("inline; filename=\"" + file.getName() + "\""));

		/*
		 * "Content-Disposition : attachment" will be directly download, may
		 * provide save as popup, based on your browser setting
		 */
		// response.setHeader("Content-Disposition", String.format("attachment;
		// filename=\"%s\"", file.getName()));

		response.setContentLength((int) file.length());

		InputStream inputStream = new BufferedInputStream(new FileInputStream(file));

		// Copy bytes from source to destination(outputstream in this example),
		// closes both streams.
		FileCopyUtils.copy(inputStream, response.getOutputStream());
	}

	@RequestMapping(value = "/saveDevices", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Device> saveDevices(@RequestBody Device device) throws Exception {
		if (device != null) {
			deviceRepository.saveAndFlush(device);
		}
		return new ResponseEntity<Device>(device, HttpStatus.OK);
	}

	@RequestMapping(value = "/fetchCommand/{deviceID}/{analogOutput}/{previousPwd}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getCommandData(@PathVariable("deviceID") String deviceID,
			@PathVariable("analogOutput") String analogOutput, @PathVariable("previousPwd") String password) {
		CommandsConfigurationVO commandsConfigurationVO = new CommandsConfigurationVO();
		int[] outputBit = null;
		try {
			String[] analogOut = analogOutput.split("_");
			float[] analogOutputValues = new float[analogOut.length];

			for (int i = 0; i < analogOut.length; i++) {
				analogOutputValues[i] = Float.parseFloat(analogOut[i]);
			}

			outputBit = getDeviceOutput(deviceID, Integer.parseInt(password), analogOutputValues);

			// Following Condition to check isAuto Flag is enabled for Device.
			if (outputBit != null) {
				for (int i = 0; i < outputBit.length; i++) {
					Control control = controlService.getControlsByDeviceIdAndOutputBit(device.getDeviceId(), "d" + i);

					if (control.getId() != null) {
						if (control.getIsAuto() != null && control.getIsAuto() != 1) {
							outputBit[i] = 2;
						} else {
							if (outputBit[i] != 2) {
								if (outputBit[i] == 1) {
									control.setState(0);
								} else {
									control.setState(1);
								}								
								controlService.saveDeviceControl(control);
							}
						}
					}

				}
			}

			commandsConfigurationVO.setValues(outputBit);
			commandsConfigurationVO.setPassword(newPassWord);
			commandsConfigurationVO.setSleepInterval(device.getTimeInterval());
			commandsConfigurationVO.setDeviceType(device.getDeviceType());
		} catch (NumberFormatException e) {
			commandsConfigurationVO.setErrorCode("16010");
			return new ResponseEntity<Object>(commandsConfigurationVO, HttpStatus.OK);
		} catch (NullPointerException e) {
			commandsConfigurationVO.setErrorCode("16012");
			e.printStackTrace();
			return new ResponseEntity<Object>(commandsConfigurationVO, HttpStatus.OK);
		} catch (SQLGrammarException e) {
			commandsConfigurationVO.setErrorCode("16014");
			return new ResponseEntity<Object>(commandsConfigurationVO, HttpStatus.OK);
		} catch (Exception e) {
			commandsConfigurationVO.setErrorCode("16020");
			return new ResponseEntity<Object>(commandsConfigurationVO, HttpStatus.OK);
		}

		return new ResponseEntity<Object>(commandsConfigurationVO, HttpStatus.OK);
	}

	Device device;

	public int[] getDeviceOutput(String deviceId, int previousPwd, float[] anlogInput) {
		int[] currentCalculatedOutputBitValues = null;
		int outputDeviceState[] = null;

		if (deviceId != null) {
			device = deviceService.findByDeviceId(deviceId);

			if (device != null && device.getIsEnabled()) {
				//if (device.getPreviousPwd() == previousPwd) {

				// generate 4 digits password
				newPassWord = deviceService.generatePIN();

				if (device.getDeviceType() != null) {
					if (device.getDeviceType() == 1) {
						// 16 inputs 4 outputs
						currentCalculatedOutputBitValues = deviceService.actualCalculatedOutputBitsTypeOne(device,
								anlogInput);
					} else if (device.getDeviceType() == 2) {
						// 8 inputs and 8 outputs
						currentCalculatedOutputBitValues = deviceService.actualCalculatedOutputBitsTypeTwo(device,
								anlogInput);
					}

					// set new password and update device object
					device.setPreviousPwd(newPassWord);

					outputDeviceState = new int[currentCalculatedOutputBitValues.length];

					// String newNextCalculatedOutputBits = "";

					// Based on the previously predicted deviceState set the
					// output device state to ON/OFF
					for (int i = 0; i < outputDeviceState.length; i++) {
						String prevCalculatedOutputBits = device.getNextPredictedOutputBits();
						if ((prevCalculatedOutputBits.substring(i, i + 1).equalsIgnoreCase("1"))
								&& currentCalculatedOutputBitValues[i] == 1) {
							outputDeviceState[i] = 1; // Relay is ON

							// prevCalculatedOutputBits =
							// newNextCalculatedOutputBits + "0"; // Reset.

						} else {
							if (i == 0) {
								outputDeviceState[i] = device.getLastActionTakenPk0() == -10 ? 2 : 0;
							} else if (i == 1) {
								outputDeviceState[i] = device.getLastActionTakenPk1() == -10 ? 2 : 0;
							} else if (i == 2) {
								outputDeviceState[i] = device.getLastActionTakenPk2() == -10 ? 2 : 0;
							} else if (i == 3) {
								outputDeviceState[i] = device.getLastActionTakenPk3() == -10 ? 2 : 0;
							} else if (i == 4) {
								outputDeviceState[i] = device.getLastActionTakenPk4() == -10 ? 2 : 0;
							} else if (i == 5) {
								outputDeviceState[i] = device.getLastActionTakenPk5() == -10 ? 2 : 0;
							} else if (i == 6) {
								outputDeviceState[i] = device.getLastActionTakenPk6() == -10 ? 2 : 0;
							} else if (i == 7) {
								outputDeviceState[i] = device.getLastActionTakenPk7() == -10 ? 2 : 0;
							}
						}
					}

					deviceService.updateDeviceData(device, anlogInput, currentCalculatedOutputBitValues,
							outputDeviceState);// Save to Device And Device Data
												// Table.
				}
			  //}
			}
		}
		return outputDeviceState;
	}

	@RequestMapping(value = "/resetnextPredictionBit/{device}/{type}/{days}/{hours}/{minutes}/{selectedBits}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Device> resetNextPredictionBitForOverrides(@PathVariable("device") Long device,
			@PathVariable("type") String type, @PathVariable("days") int days, @PathVariable("hours") int hours,
			@PathVariable("minutes") int minutes, @PathVariable("selectedBits") String selectedBits) throws Exception {
		Device updateDevice = deviceService.updateDeviceOverrideRules(device, type, days, hours, minutes, selectedBits);
		return new ResponseEntity<Device>(updateDevice, HttpStatus.OK);
	}

	/*
	 * Following Method is to Get State of Device Output. For every 20 seconds
	 * this function will execute.
	 */
	@RequestMapping(value = "/fetchControlCommand/{deviceID}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> fetchControlCommand(@PathVariable("deviceID") String deviceID) {
		ControlConfigVO controlConfigVO = new ControlConfigVO();
		try {
			Device device = deviceService.findByDeviceId(deviceID);
			// String deviceOutputBitValue = "";
			int outputDeviceState[] = null;

			List<Control> control = controlService.fetchControlCommand(deviceID);

			if (device.getDeviceType() == 2) {
				outputDeviceState = new int[8];
				for (int i = 0; i < outputDeviceState.length; i++) {
					outputDeviceState[i] = 2;
				}

				for (Control tempControl : control) {
					if (tempControl.getOutputBit().equalsIgnoreCase("D0")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[0] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D1")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[1] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D2")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[2] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D3")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[3] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D4")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[4] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D5")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[5] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D6")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[6] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D7")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[7] = tempControl.getState();
							}
						}
					}

					// Following code to update the control state notify flag to
					// '0'.
					tempControl.setIsNotify(0);
					controlService.saveDeviceControl(tempControl);
				}
			}

			if (device.getDeviceType() == 1) {
				outputDeviceState = new int[4];
				for (int i = 0; i < outputDeviceState.length; i++) {
					outputDeviceState[i] = 2;
				}

				for (Control tempControl : control) {
					if (tempControl.getOutputBit().equalsIgnoreCase("D0")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[0] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D1")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[1] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D2")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[2] = tempControl.getState();
							}
						}
					}
					if (tempControl.getOutputBit().equalsIgnoreCase("D3")) {
						if (tempControl.getIsAuto() != null) {
							if (tempControl.getIsAuto() != 1) {
								outputDeviceState[3] = tempControl.getState();
							}
						}
					}

					// Following code to update the control state notify flag to
					// '0'.
					tempControl.setIsNotify(0);
					controlService.saveDeviceControl(tempControl);
				}
			}
			controlConfigVO.setValues(outputDeviceState);
			controlConfigVO.setDeviceType(device.getDeviceType());
		} catch (NumberFormatException e) {
			System.out.println(e.getCause());
			controlConfigVO.setErrorCode("16010");
			return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
		} catch (NullPointerException e) {
			System.out.println(e.getCause());
			controlConfigVO.setErrorCode("16012");
			return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
		} catch (SQLGrammarException e) {
			System.out.println(e.getCause());
			controlConfigVO.setErrorCode("16014");
			return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
		} catch (Exception e) {
			System.out.println(e.getCause());
			controlConfigVO.setErrorCode("16020");
			return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
		}

		return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
	}

	/*
	 * Following Method is to Save the Bits to control Device.
	 */
	@RequestMapping(value = "/saveDeviceControls", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Control> saveDeviceControls(@RequestBody Control control) throws Exception {
		Control newControl = controlService.getControlsByDeviceIdAndOutputBit(control.getDeviceId(),
				control.getOutputBit());
		newControl.setIsNotify(1);
		newControl.setIsAuto(control.getIsAuto());
		newControl.setState(control.getState());
		newControl.setShutdown(control.getShutdown());
		newControl.setShutdownLogs(control.getShutdownLogs());
		newControl.setUpdatedBy(control.getUpdatedBy());
		newControl.setDeviceId(control.getDeviceId());
		newControl.setOutputBit(control.getOutputBit());
		newControl.setDelayAction(control.getDelayAction());
		control = controlService.saveDeviceControl(newControl);

		Device device = deviceService.findByDeviceId(control.getDeviceId());

		if (control.getShutdown() != null && control.getShutdown() == -10) {
			Long deviceDataId = deviceService.getLastActionTakenDeviceId(device.getId());
			if (deviceDataId != null) {
				if ("d0".equals(control.getOutputBit())) {
					device.setLastActionTakenPk0(deviceDataId);
				}
				if ("d1".equals(control.getOutputBit())) {
					device.setLastActionTakenPk1(deviceDataId);
				}
				if ("d2".equals(control.getOutputBit())) {
					device.setLastActionTakenPk2(deviceDataId);
				}
				if ("d3".equals(control.getOutputBit())) {
					device.setLastActionTakenPk3(deviceDataId);
				}
				if ("d4".equals(control.getOutputBit())) {
					device.setLastActionTakenPk4(deviceDataId);
				}
				if ("d5".equals(control.getOutputBit())) {
					device.setLastActionTakenPk5(deviceDataId);
				}
				if ("d6".equals(control.getOutputBit())) {
					device.setLastActionTakenPk6(deviceDataId);
				}
				if ("d7".equals(control.getOutputBit())) {
					device.setLastActionTakenPk7(deviceDataId);
				}
			}
		}
		deviceRepository.save(device);

		// Following code save control bit and state in output.txt file
		List<AppConstant> filePathTemp = appConstantService.getFileUploadPath();
		String FileUploadPathTemp = "";
		for (AppConstant appConstant : filePathTemp) {
			if (appConstant.getCode().equalsIgnoreCase("fileOutputPathTemp")) {
				FileUploadPathTemp = appConstant.getValue();
			}
		}
		FileWriter writer = null;
		try {
			File deviceContolFile = new File(FileUploadPathTemp + control.getDeviceId() + ".results.output.txt");
			deviceContolFile.setExecutable(true, false);
			deviceContolFile.setReadable(true, false);
			deviceContolFile.setWritable(true, false);

			writer = new FileWriter(deviceContolFile, true);

			Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();

			// add owners permission
			perms.add(PosixFilePermission.OWNER_READ);
			perms.add(PosixFilePermission.OWNER_WRITE);
			perms.add(PosixFilePermission.OWNER_EXECUTE);
			// add group permissions
			perms.add(PosixFilePermission.GROUP_READ);
			perms.add(PosixFilePermission.GROUP_WRITE);
			perms.add(PosixFilePermission.GROUP_EXECUTE);
			// add others permissions
			perms.add(PosixFilePermission.OTHERS_READ);
			perms.add(PosixFilePermission.OTHERS_WRITE);
			perms.add(PosixFilePermission.OTHERS_EXECUTE);

			if(SystemUtils.IS_OS_LINUX) {
				Files.setPosixFilePermissions(Paths.get(FileUploadPathTemp + control.getDeviceId() + ".results.output.txt"),
					perms);
			}

			StringBuilder sb = new StringBuilder();
			if (device != null) {
				sb.append("{\n");
				sb.append("\"deviceType\":" + device.getDeviceType() + ",\n");
				sb.append("\"values\":[\n");
				if (device.getDeviceType() == 1) {
					String[] strArr = null;
					if (deviceContolFile.length() != 0) {
						String line4 = Files
								.readAllLines(
										Paths.get(FileUploadPathTemp + control.getDeviceId() + ".results.output.txt"))
								.get(3);
						strArr = line4.split(",");
					}
					for (int i = 0; i < 4; i++) {
						if (control.getOutputBit().equals("d" + i)) {
							sb.append(control.getState() + ",");
						} else {
							if (strArr != null) {
								sb.append(strArr[i] + ",");
							} else {
								sb.append("0,");
							}
						}
					}
				} else if (device.getDeviceType() == 2) {
					String[] strArr = null;
					if (deviceContolFile.length() != 0) {
						String line4 = Files
								.readAllLines(
										Paths.get(FileUploadPathTemp + control.getDeviceId() + ".results.output.txt"))
								.get(3);
						strArr = line4.split(",");
					}
					for (int i = 0; i < 8; i++) {
						if (control.getOutputBit().equals("d" + i)) {
							sb.append(control.getState() + ",");
						} else {
							if (strArr != null) {
								sb.append(strArr[i] + ",");
							} else {
								sb.append("0,");
							}
						}
					}
				}
				sb.deleteCharAt(sb.lastIndexOf(","));
				sb.append("\n]\n}");
			}
			FileUtils.write(deviceContolFile, "");
			writer.write(sb.toString());
		} finally {
			if (writer != null) {
				writer.close();
			}
		}

		return new ResponseEntity<Control>(control, HttpStatus.OK);
	}

	/*
	 * Following method to fetch the list from Device Control Entity.
	 */
	@Transactional
	@RequestMapping(value = "/fetchControlsByDeviceId", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Control>> fetchControlsByDeviceId(String deviceId) throws Exception {
		List<Control> controls = new ArrayList<Control>();
		controls = controlService.fetchControlsByDeviceId(deviceId);
		return new ResponseEntity<List<Control>>(controls, HttpStatus.OK);
	}

	/*
	 * Following Method is to Save the Bits to control Device.
	 */
	@RequestMapping(value = "/saveDeviceControlsInFile", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Control> saveDeviceControlsInFile(@RequestBody Control control) throws Exception {
		control.setIsNotify(1);
		control = controlService.saveDeviceControl(control);

		// Following code save control bit and state in output.txt file
		List<AppConstant> filePathTemp = appConstantService.getFileUploadPath();
		String FileUploadPathTemp = "";
		for (AppConstant appConstant : filePathTemp) {
			if (appConstant.getCode().equalsIgnoreCase("fileUploadPathTemp")) {
				FileUploadPathTemp = appConstant.getValue();
			}
		}
		FileWriter writer = null;
		try {
			File dir = new File(FileUploadPathTemp + control.getDeviceId() + ".results");
			if (!dir.exists()) {
				dir.mkdir();
			}
			File deviceContolFile = new File(FileUploadPathTemp + control.getDeviceId() + ".results" + "\\output.txt");
			writer = new FileWriter(deviceContolFile);
			String header = control.getOutputBit() + "#" + control.getState();
			writer.write(header);
		} finally {
			if (writer != null) {
				writer.close();
			}
		}

		return new ResponseEntity<Control>(control, HttpStatus.OK);
	}

	/*
	 * Following method to fetch the device type 3 output bits
	 */
	@RequestMapping(value = "/fetchControlCommandType3/{deviceID}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> fetchControlCommandType3(@PathVariable("deviceID") String deviceID) throws Exception {
		ControlConfigVO controlConfigVO = new ControlConfigVO();
		try {
			Device device = deviceService.findByDeviceId(deviceID);
			int outputDeviceState[] = new int[8];

			List<Control> control = controlService.fetchControlCommand(deviceID);

			for (int i = 0; i < outputDeviceState.length; i++) {
				outputDeviceState[i] = 2;
			}

			for (Control tempControl : control) {
				if (tempControl.getOutputBit().equalsIgnoreCase("D0")) {
					outputDeviceState[0] = tempControl.getState();
				}
				if (tempControl.getOutputBit().equalsIgnoreCase("D1")) {
					outputDeviceState[1] = tempControl.getState();
				}
				if (tempControl.getOutputBit().equalsIgnoreCase("D2")) {
					outputDeviceState[2] = tempControl.getState();
				}
				if (tempControl.getOutputBit().equalsIgnoreCase("D3")) {
					outputDeviceState[3] = tempControl.getState();
				}
				if (tempControl.getOutputBit().equalsIgnoreCase("D4")) {
					outputDeviceState[4] = tempControl.getState();
				}
				if (tempControl.getOutputBit().equalsIgnoreCase("D5")) {
					outputDeviceState[5] = tempControl.getState();
				}
				if (tempControl.getOutputBit().equalsIgnoreCase("D6")) {
					outputDeviceState[6] = tempControl.getState();
				}
				if (tempControl.getOutputBit().equalsIgnoreCase("D7")) {
					outputDeviceState[7] = tempControl.getState();
				}

				// Following code to update the control state notify flag to
				// '0'.
				tempControl.setIsNotify(0);
				controlService.saveDeviceControl(tempControl);
			}

			controlConfigVO.setValues(outputDeviceState);
			controlConfigVO.setDeviceType(device.getDeviceType());

		} catch (NumberFormatException e) {
			System.out.println(e.getCause());
			controlConfigVO.setErrorCode("16010");
			return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
		} catch (NullPointerException e) {
			System.out.println(e.getCause());
			controlConfigVO.setErrorCode("16012");
			return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
		} catch (SQLGrammarException e) {
			System.out.println(e.getCause());
			controlConfigVO.setErrorCode("16014");
			return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
		} catch (Exception e) {
			System.out.println(e.getCause());
			controlConfigVO.setErrorCode("16020");
			return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
		}

		return new ResponseEntity<Object>(controlConfigVO, HttpStatus.OK);
	}
}
