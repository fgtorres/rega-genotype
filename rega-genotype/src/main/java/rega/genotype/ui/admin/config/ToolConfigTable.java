package rega.genotype.ui.admin.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import rega.genotype.config.Config.ToolConfig;
import rega.genotype.config.ToolManifest;
import rega.genotype.config.ToolUpdateService;
import rega.genotype.service.ToolRepoServiceRequests;
import rega.genotype.service.ToolRepoServiceRequests.ToolRepoServiceExeption;
import rega.genotype.ui.admin.AdminNavigation;
import rega.genotype.ui.admin.config.ToolConfigTableModel.ToolConfigTableModelSortProxy;
import rega.genotype.ui.admin.config.ToolConfigTableModel.ToolInfo;
import rega.genotype.ui.admin.config.ToolConfigTableModel.ToolState;
import rega.genotype.ui.framework.widgets.DownloadButton;
import rega.genotype.ui.framework.widgets.MsgDialog;
import rega.genotype.ui.framework.widgets.StandardDialog;
import rega.genotype.ui.framework.widgets.Template;
import rega.genotype.ui.util.FileUpload;
import rega.genotype.utils.FileUtil;
import rega.genotype.utils.Settings;
import eu.webtoolkit.jwt.CheckState;
import eu.webtoolkit.jwt.SelectionBehavior;
import eu.webtoolkit.jwt.SelectionMode;
import eu.webtoolkit.jwt.Signal;
import eu.webtoolkit.jwt.Signal1;
import eu.webtoolkit.jwt.Signal2;
import eu.webtoolkit.jwt.SortOrder;
import eu.webtoolkit.jwt.WCheckBox;
import eu.webtoolkit.jwt.WContainerWidget;
import eu.webtoolkit.jwt.WDialog;
import eu.webtoolkit.jwt.WDialog.DialogCode;
import eu.webtoolkit.jwt.WLength;
import eu.webtoolkit.jwt.WModelIndex;
import eu.webtoolkit.jwt.WMouseEvent;
import eu.webtoolkit.jwt.WPushButton;
import eu.webtoolkit.jwt.WStackedWidget;
import eu.webtoolkit.jwt.WTableView;
import eu.webtoolkit.jwt.WText;
import eu.webtoolkit.jwt.servlet.UploadedFile;

/**
 * Show list of tools that can be edited with ToolConfigDialog.
 * 
 * @author michael
 */
public class ToolConfigTable extends Template{
	private ToolConfigTableModelSortProxy proxyModel;
	private WText infoT = new WText();
	private WStackedWidget stack;
	
	public ToolConfigTable(WStackedWidget stack) {
		super(tr("admin.config.tool-config-table"));
		this.stack = stack;

		bindWidget("info", infoT);
		
		final WCheckBox versionChB = new WCheckBox("Show old versions");
		final WCheckBox remoteChB = new WCheckBox("Show remote tools");

		List<ToolManifest> remoteManifests = getRemoteManifests();

		// get local tools
		List<ToolManifest> localManifests = getLocalManifests();
		
		// create table
		ToolConfigTableModel model = new ToolConfigTableModel(
				localManifests, remoteManifests);
		final WTableView table = new WTableView();
		table.setSelectionMode(SelectionMode.SingleSelection);
		table.setSelectionBehavior(SelectionBehavior.SelectRows);
		table.setHeight(new WLength(400));


		proxyModel = new ToolConfigTableModelSortProxy(model);
		table.setModel(proxyModel);
		table.sortByColumn(2, SortOrder.AscendingOrder);

		for (int c = 0; c < model.getColumnCount(); ++c)
			table.setColumnWidth(c, model.getColumnWidth(c));

		final WPushButton addB = new WPushButton("Add");
		final WPushButton editB = new WPushButton("Edit");
		final WPushButton installB = new WPushButton("Install");
		final WPushButton newVersionB = new WPushButton("Create new version");
		final WPushButton uninstallB = new WPushButton("Uninstall");
		final WPushButton updateB = new WPushButton("Update");
		final WPushButton importB = new WPushButton("Import");
		
		// downloadB
		final DownloadButton downloadB = new DownloadButton("Export") {
			@Override
			public File downlodFile() {
				if (table.getSelectedIndexes().size() == 1) {
					ToolInfo toolInfo = proxyModel.getToolInfo(
							table.getSelectedIndexes().first());
					File toolFile = toolInfo.getConfig().getConfigurationFile();
					if (toolFile == null || !toolFile.exists()
							|| toolInfo.getManifest() == null) {
						return null;
					}

					File zip = new File(Settings.getInstance().getBasePackagedToolsDir() 
							+ File.separator + toolInfo.getManifest().getUniqueToolId() + ".zip");
					if (zip.exists())
						zip.delete();
					zip.getParentFile().mkdirs();
					try {
						zip.createNewFile();
					} catch (IOException e) {
						e.printStackTrace();
						return null;
					}

					FileUtil.zip(toolFile, zip);
					return zip;
				}

				return null;
			}
		};
		downloadB.disable();

		installB.disable();

		installB.clicked().addListener(installB, new Signal.Listener() {
			public void trigger() {
				if (table.getSelectedIndexes().size() == 1) {
					ToolInfo toolInfo = proxyModel.getToolInfo(
							table.getSelectedIndexes().first());

					if (toolInfo.getState() == ToolState.RemoteNotSync) {
						ToolManifest manifest = toolInfo.getManifest();
						if (Settings.getInstance().getConfig().getToolConfigById(
								manifest.getId(), manifest.getVersion()) != null) {
							new MsgDialog("Install faied", "Tool id = " + manifest.getId() 
									+ ", version = " + manifest.getVersion() 
									+ " alredy exists on local server."); 
							return;
						}

						File f = null;
						try {
							f = ToolRepoServiceRequests.getTool(manifest.getId(), manifest.getVersion());
						} catch (ToolRepoServiceExeption e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}

						importTool(manifest, f);
					}
				}
			}
		});

		uninstallB.clicked().addListener(uninstallB, new Signal.Listener() {
			public void trigger() {
				if (table.getSelectedIndexes().size() == 1) {
					ToolInfo toolInfo = proxyModel.getToolInfo(
							table.getSelectedIndexes().first());
					try {
						FileUtils.deleteDirectory(new File(toolInfo.getConfig().getConfiguration()));
					} catch (IOException e) {
						e.printStackTrace();
						new MsgDialog("Error", "Could not delete tool, Error: " + e.getMessage()); 
					}
					Settings.getInstance().getConfig().removeTool(toolInfo.getConfig());
					try {
						Settings.getInstance().getConfig().save();
					} catch (IOException e) {
						e.printStackTrace();
						assert(false);
						new MsgDialog("Info", "Global config not save, due to IO error.");
					}

					proxyModel.refresh(getLocalManifests(), getRemoteManifests());
				}

			}
		});

		updateB.clicked().addListener(updateB, new Signal.Listener() {
			public void trigger() {
				if (table.getSelectedIndexes().size() == 1) {
					ToolInfo toolInfo = proxyModel.getToolInfo(
							table.getSelectedIndexes().first());
					ToolUpdateService.update(getRemoteManifests(), toolInfo.getManifest().getId());
					proxyModel.refresh(getLocalManifests(), getRemoteManifests());
				}
			}
		});

		importB.clicked().addListener(importB, new Signal.Listener() {
			public void trigger() {
				StandardDialog d= new StandardDialog("Import");
				final FileUpload fileUpload = new FileUpload();
				fileUpload.getWFileUpload().setFilters(".zip");
				d.getContents().addWidget(new WText("Choose import file"));
				d.getContents().addWidget(fileUpload);
				d.finished().addListener(d,  new Signal1.Listener<WDialog.DialogCode>() {
					public void trigger(DialogCode arg) {
						if (arg == DialogCode.Accepted) {
							if (fileUpload.getWFileUpload().getUploadedFiles().size() == 1) {
								UploadedFile f = fileUpload.getWFileUpload().getUploadedFiles().get(0);
								
								String manifestJson = FileUtil.getFileContent(new File(f.getSpoolFileName()), 
										ToolManifest.MANIFEST_FILE_NAME);
								ToolManifest manifest = ToolManifest.parseJson(manifestJson);
								if (manifest != null) {
									importTool(manifest, new File(f.getSpoolFileName()));
								} else {
									new MsgDialog("Error", "Invalid tool file");
								}
							}
						}
					}
				});

				proxyModel.refresh(getLocalManifests(), getRemoteManifests());
			}
		});

		addB.clicked().addListener(addB, new Signal.Listener() {
			public void trigger() {
				AdminNavigation.setNewToolUrl();
			}
		});

		newVersionB.clicked().addListener(newVersionB, new Signal.Listener() {
			public void trigger() {
				if (table.getSelectedIndexes().size() == 1) {
					ToolInfo toolInfo = proxyModel.getToolInfo(table.getSelectedIndexes().first());
					AdminNavigation.setNewVersionToolUrl(
							toolInfo.getManifest().getId(),
							toolInfo.getManifest().getVersion());
				}
			}
		});

		table.doubleClicked().addListener(table, new Signal2.Listener<WModelIndex, WMouseEvent>() {

			public void trigger(WModelIndex index, WMouseEvent arg2) {
				if (index == null)
					return;
				ToolInfo toolInfo = proxyModel.getToolInfo(index);
				if (toolInfo.getState() == ToolState.RemoteNotSync)
					return;

				AdminNavigation.setEditToolUrl(
						toolInfo.getManifest().getId(),
						toolInfo.getManifest().getVersion());
			}
		});

		editB.clicked().addListener(editB, new Signal.Listener() {
			public void trigger() {
				if (table.getSelectedIndexes().size() == 1) {
					ToolInfo toolInfo = proxyModel.getToolInfo(table.getSelectedIndexes().first());
					AdminNavigation.setEditToolUrl(
							toolInfo.getManifest().getId(),
							toolInfo.getManifest().getVersion());
				}
			}
		});

		table.selectionChanged().addListener(this, new Signal.Listener() {
			public void trigger() {
				if (table.getSelectedIndexes().size() == 1) {
					ToolInfo toolInfo = proxyModel.getToolInfo(
							table.getSelectedIndexes().first());
					installB.setEnabled(toolInfo.getState() == ToolState.RemoteNotSync);

					editB.setEnabled(toolInfo.getState() != ToolState.RemoteNotSync);
					newVersionB.setEnabled(toolInfo.getState() != ToolState.RemoteNotSync);

					// only the last version can be updated 
					updateB.setEnabled(!proxyModel.getToolConfigTableModel().isUpToDate(
							toolInfo.getManifest().getId()));

					// uninstall
					uninstallB.setEnabled(toolInfo.getState() != ToolState.RemoteNotSync);
					if (toolInfo.getState() == ToolState.Local)
						uninstallB.setText("Remove");
					else
						uninstallB.setText("Uninstall");
				
					downloadB.enable();
				} else {
					installB.disable();
					editB.disable();
					newVersionB.disable();
					uninstallB.disable();
					updateB.disable();
					downloadB.disable();
				}
			}
		});
		table.selectionChanged().trigger();

		versionChB.changed().addListener(this, new Signal.Listener() {
			public void trigger() {
				proxyModel.setFilterOldVersion(
						versionChB.getCheckState() != CheckState.Checked);
			}
		});
		versionChB.setChecked(false);
		proxyModel.setFilterOldVersion(true);

		remoteChB.changed().addListener(this, new Signal.Listener() {
			public void trigger() {
				proxyModel.setFilterNotRemote(
						remoteChB.getCheckState() != CheckState.Checked);
			}
		});
		remoteChB.setChecked(false);
		proxyModel.setFilterNotRemote(true);

		bindWidget("version-chb", versionChB);
		bindWidget("remote-chb", remoteChB);
		bindWidget("table", table);
		bindWidget("add", addB);
		bindWidget("edit", editB);
		bindWidget("install", installB);
		bindWidget("new-version", newVersionB);
		bindWidget("uninstall", uninstallB);
		bindWidget("update", updateB);
		bindWidget("import", importB);
		bindWidget("export", downloadB);

	}

	private void importTool(ToolManifest manifest, File f) {
		// create tool config.
		FileUtil.unzip(f, new File(manifest.suggestXmlDirName()));
		if (f != null) {
			// create local config for installed tool
			ToolConfig config = new ToolConfig();
			config.setConfiguration(manifest.suggestXmlDirName());
			config.genetareJobDir();
			Settings.getInstance().getConfig().putTool(config);
			try {
				Settings.getInstance().getConfig().save();
			} catch (IOException e) {
				e.printStackTrace();
				assert(false); // coping to new dir should always work.
			}

			// redirect to edit screen.
			AdminNavigation.setInstallToolUrl(
					manifest.getId(),
					manifest.getVersion());
		} else {
			new MsgDialog("Error", "Invalid tool file.");
		}
	}

	private List<ToolManifest> getRemoteManifests() {
		// get remote tools
		List<ToolManifest> remoteManifests = new ArrayList<ToolManifest>();
		String manifestsJson = ToolRepoServiceRequests.getManifestsJson();
		if (manifestsJson == null || manifestsJson.isEmpty()) {
			infoT.setText("Could not read remote tools");
		} else {
			remoteManifests = ToolManifest.parseJsonAsList(manifestsJson);
			if (remoteManifests == null) {
				infoT.setText("Could not parss remote tools");
				return new ArrayList<ToolManifest>();
			}
		}

		return remoteManifests;
	}

	public void showCreateNewTool() {
		edit(null, ToolConfigForm.Mode.Add);
	}

	public void showEditTool(String toolId, String toolVersion, ToolConfigForm.Mode mode) {
		showTable(); // remove prev shown tool		

		ToolInfo toolInfo = proxyModel.getToolConfigTableModel().
				getToolInfo(toolId, toolVersion);

		if (toolInfo == null) {
			WContainerWidget c = new WContainerWidget();
			c.addWidget(new WText("Tool: id = " + toolId +
					", version = " + toolVersion + "does not exist."));
			WPushButton back = new WPushButton("Back", c);
			stack.addWidget(c);
			back.clicked().addListener(back, new Signal.Listener() {
				public void trigger() {
					showTable();
				}
			});
		} else {
			edit(toolInfo, mode);
		}
	}
	
	public void showTable() {
		while (stack.getChildren().size() > 1) {
			stack.removeWidget(stack.getWidget(1));
		}
	}

	private void edit(ToolInfo info, ToolConfigForm.Mode mode) {
		if (stack.getChildren().size() > 1) {
			return; // someone clicked too fast.
		}

		ToolConfig config = null;
		switch (mode) {
		case Add:
			config = createToolConfig();
			Settings.getInstance().getConfig().putTool(config);
			try {
				Settings.getInstance().getConfig().save();
			} catch (IOException e) {
				e.printStackTrace();
				assert(false);
			}
			break;
		case NewVersion:
			config = info.getConfig().copy();
			config.genetareDirs();
			Settings.getInstance().getConfig().putTool(config);
			try {
				Settings.getInstance().getConfig().save();
				String oldVersionDir = info.getConfig().getConfiguration();
				FileUtil.copyDirContentRecorsively(new File(oldVersionDir), 
						config.getConfiguration());
			} catch (IOException e) {
				e.printStackTrace();
				assert(false); // coping to new dir should always work.
			}

			// the manifest was also copied
			if (config.getToolMenifest() != null) {
				config.getToolMenifest().setVersion(suggestNewVersion(config, 1));
				config.getToolMenifest().save(config.getConfiguration());
			}
			break;
		case Edit:
			if (info.getConfig() == null){
				assert(false);
				return;
			} else {
				config = info.getConfig();
			}
			break;
		case Install:
			String dataDirStr = info.getManifest().suggestXmlDirName();
			File toolDir = new File(dataDirStr);
			toolDir.mkdirs();
			config = new ToolConfig();
			config.setConfiguration(dataDirStr);
			config.genetareJobDir();
			config.setPublished(true);

			try {
				Settings.getInstance().getConfig().save();
			} catch (IOException e) {
				e.printStackTrace();
				assert(false);
			}

			break;
		default:
			break;
		}

		final ToolConfigForm d = new ToolConfigForm(config, mode);
		stack.addWidget(d);
		stack.setCurrentWidget(d);
		d.done().addListener(d, new Signal.Listener() {
			public void trigger() {
				proxyModel.refresh(getLocalManifests(), getRemoteManifests());
				AdminNavigation.setToolsTableUrl();
			}
		});
	}

	private String suggestNewVersion(ToolConfig config, Integer sggestedVersion) {
		// find a version number that was not used yet.
		
		for (ToolManifest m: Settings.getInstance().getConfig().getManifests()) {
			if (m.getId().equals(config.getToolMenifest().getId())
					&& m.getVersion().equals(sggestedVersion.toString())){
				return suggestNewVersion(config, sggestedVersion + 1);
			}
		}

		return sggestedVersion.toString();
	}

	private ToolConfig createToolConfig() {
		ToolConfig config;
		config = new ToolConfig();
		config.genetareDirs();
		return config;
	}

	private List<ToolManifest> getLocalManifests() {
		List<ToolManifest> ans = new ArrayList<ToolManifest>();
		for (ToolConfig c: Settings.getInstance().getConfig().getTools()) {
			String json = FileUtil.readFile(new File(
					c.getConfigurationFile(), ToolManifest.MANIFEST_FILE_NAME));
			if (json != null) {
				ToolManifest m = ToolManifest.parseJson(json);
				if (m != null)
					ans.add(m);
			}
		}

		return ans;
	}
}
