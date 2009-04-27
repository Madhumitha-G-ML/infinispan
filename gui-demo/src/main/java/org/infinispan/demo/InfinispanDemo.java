package org.infinispan.demo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.infinispan.Cache;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryEvicted;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.Event;
import org.infinispan.notifications.cachemanagerlistener.annotation.ViewChanged;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;
import org.infinispan.remoting.transport.Address;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Manik Surtani
 */
public class InfinispanDemo {
   private static Log log = LogFactory.getLog(InfinispanDemo.class);
   private static JFrame frame;
   private JTabbedPane mainPane;
   private JPanel panel1;
   private JLabel cacheStatus;
   private JPanel dataGeneratorTab;
   private JPanel statisticsTab;
   private JPanel clusterViewTab;
   private JPanel dataViewTab;
   private JPanel controlPanelTab;
   private JTable clusterTable;
   private JButton actionButton;
   private JLabel configFileName;
   private JProgressBar cacheStatusProgressBar;
   private JTextField keyTextField;
   private JTextField valueTextField;
   private JRadioButton putEntryRadioButton;
   private JRadioButton removeEntryRadioButton;
   private JRadioButton getEntryRadioButton;
   private JButton goButton;
   private JScrollPane nodeDataScrollPane;
   private JButton randomGeneratorButton;
   private JTextField maxEntriesTextField;
   private JButton cacheClearButton;
   private JTextArea configFileContents;
   private JScrollPane treeScrollPane;
   private JPanel debugTab;
   private JButton cacheDetailsButton;
   private JButton cacheLockInfoButton;
   private JTextArea debugTextArea;
   private String cacheConfigFile;
   private Cache<String, String> cache;
   private String startCacheButtonLabel = "Start Cache", stopCacheButtonLabel = "Stop Cache";
   private String statusStarting = "Starting Cache ... ", statusStarted = "Cache Running.", statusStopping = "Stopping Cache ...", statusStopped = "Cache Stopped.";
   private ExecutorService asyncExecutor;
   private final AtomicInteger updateCounter = new AtomicInteger(0);
   private BlockingQueue<Runnable> asyncTaskQueue;
   private JTable dataTable;
   private JButton refreshDataTableButton;
   private Random r = new Random();
   private ClusterTableModel clusterTableModel;
   private CachedDataTableModel cachedDataTableModel;

   public static void main(String[] args) {
      String cfgFileName = System.getProperty("infinispan.demo.cfg", "etc/demo-cache-config.xml");
      frame = new JFrame("Infinispan GUI Demo (STOPPED)");
      frame.setContentPane(new InfinispanDemo(cfgFileName).panel1);
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.pack();
      frame.setVisible(true);
      frame.setResizable(true);
   }

   public InfinispanDemo(String cfgFileName) {
      asyncExecutor = Executors.newFixedThreadPool(1);
      asyncTaskQueue = ((ThreadPoolExecutor) asyncExecutor).getQueue();

      cacheConfigFile = cfgFileName;
      cacheStatusProgressBar.setVisible(false);
      cacheStatusProgressBar.setEnabled(false);
      configFileName.setText(cacheConfigFile);

      // data tables
      clusterTableModel = new ClusterTableModel();
      clusterTable.setModel(clusterTableModel);
      cachedDataTableModel = new CachedDataTableModel();
      dataTable.setModel(cachedDataTableModel);

      // default state of the action button should be unstarted.
      actionButton.setText(startCacheButtonLabel);
      cacheStatus.setText(statusStopped);

      // when we start up scan the classpath for a file named
      actionButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            if (actionButton.getText().equals(startCacheButtonLabel)) {
               // start cache
               startCache();
            } else if (actionButton.getText().equals(stopCacheButtonLabel)) {
               // stop cache
               stopCache();
            }
         }
      });

      goButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            processAction(goButton, true);

            // do this in a separate thread
            asyncExecutor.execute(new Runnable() {
               public void run() {
                  // based on the value of the radio button:
                  if (putEntryRadioButton.isSelected()) {
                     cache.put(keyTextField.getText(), valueTextField.getText());
                  } else if (removeEntryRadioButton.isSelected()) {
                     cache.remove(keyTextField.getText());
                  } else if (getEntryRadioButton.isSelected()) {
                     cache.get(keyTextField.getText());
                  }
                  dataViewTab.repaint();
                  processAction(goButton, false);
                  // now switch to the data pane
                  mainPane.setSelectedIndex(1);
               }
            });
         }
      });
      removeEntryRadioButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            keyTextField.setEnabled(true);
            valueTextField.setEnabled(false);
         }
      });

      putEntryRadioButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            keyTextField.setEnabled(true);
            valueTextField.setEnabled(true);
         }
      });

      getEntryRadioButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            keyTextField.setEnabled(true);
            valueTextField.setEnabled(false);
         }
      });

      randomGeneratorButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            processAction(randomGeneratorButton, true);

            // process this asynchronously
            asyncExecutor.execute(new Runnable() {
               public void run() {
                  int entries = 1;
                  try {
                     entries = Integer.parseInt(maxEntriesTextField.getText());
                  }
                  catch (NumberFormatException nfe) {
                     log.warn("Entered a non-integer for depth.  Using 1.", nfe);
                  }

                  for (int i = 0; i < entries; i++) cache.put(randomString(), randomString());

                  processAction(randomGeneratorButton, false);
                  // now switch to the data pane
                  mainPane.setSelectedIndex(1);
               }
            });
         }

         private String randomString() {
            return Integer.toHexString(r.nextInt(Integer.MAX_VALUE)).toUpperCase();
         }
      });
      cacheClearButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            processAction(cacheClearButton, true);
            asyncExecutor.execute(new Runnable() {
               public void run() {
                  cache.clear();
                  processAction(cacheClearButton, false);
                  // now switch to the data pane
                  mainPane.setSelectedIndex(1);
               }
            });
         }
      });

      refreshDataTableButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            processAction(refreshDataTableButton, true);
            asyncExecutor.execute(new Runnable() {
               public void run() {
                  InfinispanDemo.this.updateCachedDataTable();
                  processAction(refreshDataTableButton, false);
                  // now switch to the data pane
                  mainPane.setSelectedIndex(1);
               }
            });
         }
      });

      cacheDetailsButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            if (cache != null) debugTextArea.setText(cache.toString());
         }
      });
      cacheLockInfoButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            if (cache != null) debugTextArea.setText(cache.toString());
         }
      });
   }

   private void moveCacheToState(ComponentStatus state) {
      switch (state) {
         case INITIALIZING:
            cacheStatus.setText(statusStarting);
            processAction(actionButton, true);
            break;
         case RUNNING:
            setCacheTabsStatus(true);
            actionButton.setText(stopCacheButtonLabel);
            processAction(actionButton, false);
            cacheStatus.setText(statusStarted);
            updateTitleBar();
            break;
         case STOPPING:
            cacheStatus.setText(statusStopping);
            processAction(actionButton, true);
            break;
         case TERMINATED:
            setCacheTabsStatus(false);
            actionButton.setText(startCacheButtonLabel);
            processAction(actionButton, false);
            cacheStatus.setText(statusStopped);
            updateTitleBar();
      }
      controlPanelTab.repaint();
   }

   private void processAction(JButton button, boolean start) {
      button.setEnabled(!start);
      cacheStatusProgressBar.setVisible(start);
      cacheStatusProgressBar.setEnabled(start);
   }

   private String readContents(InputStream is) throws IOException {
      BufferedReader r = new BufferedReader(new InputStreamReader(is));
      String s;
      StringBuilder sb = new StringBuilder();
      while ((s = r.readLine()) != null) {
         sb.append(s);
         sb.append("\n");
      }
      return sb.toString();
   }

   private void startCache() {
      moveCacheToState(ComponentStatus.INITIALIZING);

      // actually start the cache asynchronously.
      asyncExecutor.execute(new Runnable() {
         public void run() {
            try {
               if (cache == null) {
                  URL resource = getClass().getClassLoader().getResource(cacheConfigFile);
                  if (resource == null) resource = new URL(cacheConfigFile);
                  // update config file display
                  configFileName.setText(resource.toString());

                  configFileName.repaint();

                  try {
                     configFileContents.setText(readContents(resource.openStream()));
                     configFileContents.setEditable(false);
                  }
                  catch (Exception e) {
                     log.warn("Unable to open config file [" + cacheConfigFile + "] for display", e);
                  }
                  configFileContents.repaint();

                  cache = new DefaultCacheManager(resource.openStream()).getCache();
               } else {
                  cache.start();
               }

               CacheListener cl = new CacheListener();
               cache.addListener(cl);
               cache.getCacheManager().addListener(cl);
               updateClusterTable(cache.getCacheManager().getMembers());

               moveCacheToState(ComponentStatus.RUNNING);
            } catch (Exception e) {
               log.error("Unable to start cache!", e);
            }
         }
      });
   }

   private void stopCache() {
      moveCacheToState(ComponentStatus.STOPPING);
      // actually stop the cache asynchronously
      asyncExecutor.execute(new Runnable() {
         public void run() {
            if (cache != null) cache.stop();
            moveCacheToState(ComponentStatus.TERMINATED);
         }
      });
   }

   private void setCacheTabsStatus(boolean enabled) {
      int numTabs = mainPane.getTabCount();
      for (int i = 1; i < numTabs; i++) mainPane.setEnabledAt(i, enabled);
      panel1.repaint();
   }

   private void updateClusterTable(List<Address> members) {
      log.debug("Updating cluster table with new member list " + members);
      clusterTableModel.setMembers(members);
      updateTitleBar();
   }

   private void updateTitleBar() {
      String title = "Infinispan GUI Demo";
      if (cache != null && cache.getStatus() == ComponentStatus.RUNNING) {
         title += " (STARTED) " + getLocalAddress() + " Cluster size: " + getClusterSize();
      } else {
         title += " (STOPPED)";
      }
      frame.setTitle(title);
   }

   private String getLocalAddress() {
      Address a = cache.getCacheManager().getAddress();
      if (a == null) return "(LOCAL mode)";
      else return a.toString();
   }

   private String getClusterSize() {
      List<Address> members = cache.getCacheManager().getMembers();
      return members == null || members.isEmpty() ? "N/A" : "" + members.size();
   }

   @Listener
   public class CacheListener {
      @ViewChanged
      public void viewChangeEvent(ViewChangedEvent e) {
         updateClusterTable(e.getNewMemberList());
      }

      @CacheEntryModified
      @CacheEntryRemoved
      @CacheEntryEvicted
      public void removed(Event e) {
         if (!e.isPre()) updateCachedDataTable();
      }
   }

   private void updateCachedDataTable() {
      updateCounter.incrementAndGet();
      asyncExecutor.execute(new Runnable() {
         public void run() {
            if (updateCounter.decrementAndGet() == 0) cachedDataTableModel.update();
         }
      });
   }

   public class ClusterTableModel extends AbstractTableModel {
      List<String> members = new ArrayList<String>();
      List<String> memberStates = new ArrayList<String>();

      public void setMembers(List<Address> m) {
         if (m != null && !m.isEmpty()) {
            members = new ArrayList<String>(m.size());
            for (Address ma : m) members.add(ma.toString());

            memberStates = new ArrayList<String>(m.size());
            for (Address a : m) {
               String extraInfo = "Member";
               // if this is the first member then this is the coordinator
               if (memberStates.isEmpty()) extraInfo += " (coord)";
               if (a.equals(cache.getCacheManager().getAddress()))
                  extraInfo += " (me)";

               memberStates.add(extraInfo);
            }
         } else {
            members = Collections.singletonList("me!");
            memberStates = Collections.singletonList("(local mode)");
         }

         fireTableDataChanged();
      }

      public int getRowCount() {
         return members.size();
      }

      public int getColumnCount() {
         return 2;
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
         switch (columnIndex) {
            case 0:
               return members.get(rowIndex);
            case 1:
               return memberStates.get(rowIndex);
         }
         return "NULL!";
      }

      @Override
      public String getColumnName(int c) {
         if (c == 0) return "Member Address";
         if (c == 1) return "Member Info";
         return "NULL!";
      }
   }

   public class CachedDataTableModel extends AbstractTableModel {

      List<InternalCacheEntry> data = new LinkedList<InternalCacheEntry>();

      public int getRowCount() {
         return data.size();
      }

      public int getColumnCount() {
         return 4;
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
         if (data.size() > rowIndex) {
            InternalCacheEntry e = data.get(rowIndex);
            switch (columnIndex) {
               case 0:
                  return e.getKey();
               case 1:
                  return e.getValue();
               case 2:
                  return e.getLifespan();
               case 3:
                  return e.getMaxIdle();
            }
         }
         return "NULL!";
      }

      @Override
      public String getColumnName(int c) {
         switch (c) {
            case 0:
               return "Key";
            case 1:
               return "Value";
            case 2:
               return "Lifespan";
            case 3:
               return "MaxIdle";
         }
         return "NULL!";
      }

      public void update() {
         // whew - expensive stuff.
         data.clear();
         for (InternalCacheEntry ice : cache.getAdvancedCache().getDataContainer()) {
            if (!ice.isExpired()) data.add(ice);
         }
         fireTableDataChanged();
      }
   }

   class CachedEntry {
      String key, value;
      long lifespan = -1, maxIdle = -1;

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         CachedEntry that = (CachedEntry) o;

         if (lifespan != that.lifespan) return false;
         if (maxIdle != that.maxIdle) return false;
         if (key != null ? !key.equals(that.key) : that.key != null) return false;
         if (value != null ? !value.equals(that.value) : that.value != null) return false;

         return true;
      }

      @Override
      public int hashCode() {
         int result = key != null ? key.hashCode() : 0;
         result = 31 * result + (value != null ? value.hashCode() : 0);
         result = 31 * result + (int) (lifespan ^ (lifespan >>> 32));
         result = 31 * result + (int) (maxIdle ^ (maxIdle >>> 32));
         return result;
      }
   }
}
