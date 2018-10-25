package macro;

import battlecode.common.*;
import static java.lang.Math.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeMap;

public class RobotPlayer {
	static RobotController rc;
	static RobotType type;
	static Team enemyTeam = Team.B;
	static final float PI = (float) Math.PI;
	
	static final int BULLET_MARGIN = 10; // how many bullets that are required to be in the bank after a robot is queued up
	static final int ARCHON_TIMEOUT = 60;
	static final Direction[] dirList = new Direction[8];
	static boolean gettingRushed = false;

	//Movement
	static float moveLength;
	static MapLocation currentLocation;
	static MapLocation targetLocation;
	static Direction targetDirection;
	static Direction casualDirection;
	
	//Scanning
	static RobotInfo[] enemyRobots;
	static RobotInfo[] friendlyRobots;
	static TreeInfo[] trees;
	static BulletInfo[] bullets;
	
	//Build Counts
	static final int BUILD_GARDENER_MAX_COUNT = 1;
	static final int FARM_GARDENER_MAX_COUNT = 5;
	static final int INITIAL_SCOUT_BUILD_COUNT = 1;
	static final int INITIAL_LUMBERJACK_MAX_BUILD_COUNT = 4;
	static boolean screwEarlyLumberjacksFFSWeHavePlentyOfRoom = false;
	static int lumberjacksUntilGardeners = 0;
	static int soldierQuota = 2;

	// Scout
	static int superTreeID = -1;
	static boolean hasEnemyTarget = false;
	static HashSet<TreeInfo> mappedTrees = new HashSet<>();
	
	//Gardener
	static boolean gardenerIsBuilder;
	static final int IDLE_GARDENERS = 3;
	static int tankNext = 0;
	
	//Farming
    static int taskStage = 0;
	static final Direction[] plantDirections = new Direction[] {
    		new Direction(0.33347317f), 
    		new Direction(1.38067072f), 
    		new Direction(2.42786827f), 
    		new Direction(3.47506582f), 
    		new Direction(4.52226337f), 
    		new Direction(5.56946092f)
    		};
    static final Direction[] farmDirections = new Direction[] {
    		new Direction(0.0f),
    		new Direction(1.04719755f),
    		new Direction(2.09439510f),
    		new Direction(3.14159265f),
    		new Direction(4.18879020f),
    		new Direction(5.23598775f)
    		};
    static final float farmerSpacing = 5.4f;	//5.32f for tightest possible
    static final float farmerOffset = 0;

	public static void run(RobotController rcIn) throws GameActionException {
		rc = rcIn;
		if (rc.getTeam() == Team.B) {
			enemyTeam = Team.A;
		}
		
		casualDirection = rc.getLocation().directionTo(rc.getInitialArchonLocations(enemyTeam)[0]);
		type = rc.getType();
		moveLength = type.strideRadius;
		
		for (int i = 0; i < dirList.length; i++) {
			dirList[i] = new Direction(2 * PI / i);
		}
		
		switch (type) {
			case ARCHON:
				runArchon();
				break;
			case GARDENER:
				if(rc.readBroadcast(26) % 2 == 1){
					int gardenersInQueue = rc.readBroadcast(5) - 1;
					rc.broadcast(5, gardenersInQueue >= 0 ? gardenersInQueue : 0);
					gardenerIsBuilder = true;
				} else {
					targetLocation = new MapLocation(
			    			Math.round(rc.getLocation().x / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset,
			    			Math.round(rc.getLocation().y / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset
			    			);
					gardenerIsBuilder = false;
				}
				rc.broadcast(26, rc.readBroadcast(26) >>> 1);
				runGardener();
				break;
			case SOLDIER:
				targetLocation = rc.getInitialArchonLocations(enemyTeam)[0];
				targetDirection = rc.getLocation().directionTo(targetLocation);
				runSoldier();
				break;
			case LUMBERJACK:
				targetLocation = rc.getInitialArchonLocations(enemyTeam)[0];
				targetDirection = rc.getLocation().directionTo(targetLocation);
				runLumberjack();
				break;
			case SCOUT:
				targetLocation = rc.getInitialArchonLocations(enemyTeam)[0];
				targetDirection = rc.getLocation().directionTo(targetLocation);
				runScout();
				break;
			case TANK:
				targetLocation = rc.getInitialArchonLocations(enemyTeam)[0];
				targetDirection = rc.getLocation().directionTo(targetLocation);
				runTank();
				break;
		}
	}

	private static void runArchon() throws GameActionException {
		//Make out last archon
		boolean isInformer = false;
		int nrFormerArchons = rc.readBroadcast(6) + 1;
		rc.broadcast(6, nrFormerArchons);
		
		if(rc.getRoundNum() < 2){
			//Search if starting area holds lots of trees
			TreeInfo[] earlyTrees = rc.senseNearbyTrees(-1);
			rc.broadcast(9, min(max(rc.readBroadcast(9), earlyTrees.length / 2), INITIAL_LUMBERJACK_MAX_BUILD_COUNT));
			
			if(earlyTrees.length < 2){
				screwEarlyLumberjacksFFSWeHavePlentyOfRoom = true;
			}
			lumberjacksUntilGardeners = min(earlyTrees.length / 10, 2);
			
			rc.broadcastFloat(996, -1f);
			rc.broadcastFloat(997, 10000f);
			rc.broadcastFloat(998, -1f);
			rc.broadcastFloat(999, 10000f);
		}
		
		while (true) {
			try {
				System.out.println(rc.readBroadcastFloat(996));
				System.out.println(rc.readBroadcastFloat(997));
				System.out.println(rc.readBroadcastFloat(998));
				System.out.println(rc.readBroadcastFloat(999));
				checkVictoryPoints();
				currentLocation = rc.getLocation();
				
				//clear old enemy archon channels
				clearEnemyArchonChannels();
				
				enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
				if (rc.readBroadcastFloat(998) == -1f && !rc.onTheMap(currentLocation.add(0, type.sensorRadius - 0.1f))) {
					MapLocation loc = currentLocation;
					for(int i = 1; i < 200; i++){
						loc = loc.add(0, 0.05f);
						System.out.println(loc);
						if(!rc.onTheMap(loc)){
							rc.broadcastFloat(998, loc.x);
						}
					}
				}
				if (rc.readBroadcastFloat(999) == 10000f && !rc.onTheMap(currentLocation.add(PI, type.sensorRadius - 0.1f))) {
					MapLocation loc = currentLocation;
					for(int i = 1; i < 200; i++){
						loc = loc.add(PI, 0.05f);
						if(!rc.onTheMap(loc)){
							rc.broadcastFloat(999, loc.x);
						}
					}
				}
				if (rc.readBroadcastFloat(996) == -1f && !rc.onTheMap(currentLocation.add(PI/2, type.sensorRadius - 0.1f))) {
					MapLocation loc = currentLocation;
					for(int i = 1; i < 200; i++){
						loc = loc.add(PI / 2, 0.05f);
						if(!rc.onTheMap(loc)){
							rc.broadcastFloat(996, loc.y);
						}
					}
				}
				if (rc.readBroadcastFloat(997) == 10000f && !rc.onTheMap(currentLocation.add(3 * PI / 2, type.sensorRadius - 0.1f))) {
					MapLocation loc = currentLocation;
					for(int i = 1; i < 200; i++){
						loc = loc.add(3 * PI / 2, 0.05f);
						if(!rc.onTheMap(loc)){
							rc.broadcastFloat(997, loc.y);
						}
					}
				}
				
				intelligentMove(randomDirection());
				
				
				if(rc.getRoundNum() >= 400){
					gettingRushed = false;
					rc.broadcastBoolean(29, gettingRushed);
				} else {
					if (rc.getRoundNum() % 125 == 0){
						rc.broadcastBoolean(29, false);
					}
					if(enemyRobots.length > 1){
						rc.broadcastBoolean(29, true);
					}
					gettingRushed = rc.readBroadcastBoolean(29);
				}
				
				if(enemyRobots.length > 0){
					rc.broadcastFloat(30, enemyRobots[0].getLocation().x);
					rc.broadcastFloat(31, enemyRobots[0].getLocation().y);
					rc.broadcast(32, rc.getRoundNum());
				}
				 
				//Need channel 6 to make out the final archon
				if (rc.getRoundNum() != 1 && nrFormerArchons == rc.readBroadcast(6)) {
					isInformer = true;
				}

				//Build gardeners
				int inGardenerCount = rc.readBroadcast(3);
				if (rc.isBuildReady() && inGardenerCount + rc.readBroadcast(5) < BUILD_GARDENER_MAX_COUNT + rc.getTreeCount() / 12 || rc.getTeamBullets() > 2000) {
					if (tryBuildRobot(RobotType.GARDENER)){
						rc.broadcast(5, rc.readBroadcast(5) + 1);
						rc.broadcast(26, (rc.readBroadcast(26) << 1) + 1);
					}
				}
				rc.broadcast(4, inGardenerCount);

				//Farm gardeners
				if(!gettingRushed && rc.isBuildReady() && rc.readBroadcast(28) < IDLE_GARDENERS && rc.readBroadcast(7) >= lumberjacksUntilGardeners && rc.readBroadcast(27) < FARM_GARDENER_MAX_COUNT + rc.getTreeCount() / 6 ){
					if(tryBuildRobot(RobotType.GARDENER)){
						rc.broadcast(27, rc.readBroadcast(27) + 1);
						rc.broadcast(26, rc.readBroadcast(26) << 1);
						rc.broadcast(28, rc.readBroadcast(28) + 1);
					}
				}
				
				//Do this if this archon is the final archon
				if (isInformer) {
					rc.broadcast(3, 0);
				}
	
				Clock.yield();
			} catch (GameActionException e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
	}

	private static void runGardener() throws GameActionException {
		while (true) {
			try {
				checkVictoryPoints();
				currentLocation = rc.getLocation();
				
				if (rc.readBroadcastFloat(998) == -1f && !rc.onTheMap(currentLocation.add(0, type.sensorRadius - 0.1f))) {
					MapLocation loc = currentLocation;
					for(int i = 1; i < 200; i++){
						loc = loc.add(0, 0.05f);
						if(!rc.onTheMap(loc)){
							rc.broadcastFloat(998, loc.x);
						}
					}
				}
				if (rc.readBroadcastFloat(999) == 10000f && !rc.onTheMap(currentLocation.add(PI, type.sensorRadius - 0.1f))) {
					MapLocation loc = currentLocation;
					for(int i = 1; i < 200; i++){
						loc = loc.add(PI, 0.05f);
						if(!rc.onTheMap(loc)){
							rc.broadcastFloat(999, loc.x);
						}
					}
				}
				if (rc.readBroadcastFloat(996) == -1f && !rc.onTheMap(currentLocation.add(PI/2, type.sensorRadius - 0.1f))) {
					MapLocation loc = currentLocation;
					for(int i = 1; i < 200; i++){
						loc = loc.add(PI / 2, 0.05f);
						if(!rc.onTheMap(loc)){
							rc.broadcastFloat(996, loc.y);
						}
					}
				}
				if (rc.readBroadcastFloat(997) == 10000f && !rc.onTheMap(currentLocation.add(3 * PI / 2, type.sensorRadius - 0.1f))) {
					MapLocation loc = currentLocation;
					for(int i = 1; i < 200; i++){
						loc = loc.add(3 * PI / 2, 0.05f);
						if(!rc.onTheMap(loc)){
							rc.broadcastFloat(997, loc.y);
						}
					}
				}
				
				enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
				if(rc.getRoundNum() < 400 && enemyRobots.length > 1){
					System.out.println("panic");
					rc.broadcastBoolean(29, true);
					rc.broadcastFloat(30, enemyRobots[0].getLocation().x);
					rc.broadcastFloat(31, enemyRobots[0].getLocation().y);
					rc.broadcast(32, rc.getRoundNum());
				}
				
				if(rc.getRoundNum() < 400){
					gettingRushed = rc.readBroadcastBoolean(29);
				}
				
				//Gardener is builder
				if(gardenerIsBuilder){
					intelligentMove(randomDirection());
					
					// Build Scout
					if (rc.readBroadcast(8) < INITIAL_SCOUT_BUILD_COUNT + rc.getRoundNum() / 600 && rc.isBuildReady() && !gettingRushed) {
						if(tryBuildRobot(RobotType.SCOUT)){
							rc.broadcast(8, rc.readBroadcast(8) + 1);
						}
					}
					
					//Build Lumberjack
					if(!screwEarlyLumberjacksFFSWeHavePlentyOfRoom){
						if (rc.isBuildReady() && rc.readBroadcast(7) < rc.readBroadcast(9) && !gettingRushed ) {
							if(tryBuildRobot(RobotType.LUMBERJACK)){
								rc.broadcast(7, rc.readBroadcast(7) + 1);
							}
						}
					}
					
					// Build Soldier
					if (rc.isBuildReady() && rc.readBroadcast(4) + rc.readBroadcast(5) >= BUILD_GARDENER_MAX_COUNT) {
						double rand = Math.random();
						if(rand < 0.9){
							tryBuildRobot(RobotType.SOLDIER);
						} else {
							tryBuildRobot(RobotType.LUMBERJACK);
						}
					}
					
					
					// Build Soldier
//					if (rc.isBuildReady() && (tankNext < 1 || rc.getTreeCount() <= 12) && rc.readBroadcast(4) + rc.readBroadcast(5) >= BUILD_GARDENER_MAX_COUNT) {
//						if(tryBuildRobot(RobotType.SOLDIER)){
//							rc.broadcast(33, rc.readBroadcast(33) + 1);
//							tankNext++;
//						}
//					}
//					
//					if (rc.isBuildReady() && rc.getTreeCount() > 15 && rc.readBroadcast(4) + rc.readBroadcast(5) >= BUILD_GARDENER_MAX_COUNT){
//						if(tryBuildRobot(RobotType.TANK)){
//							tankNext = 0;
//						}
//					}
//					
					rc.broadcast(3, rc.readBroadcast(3) + 1);
					
				//Gardener is farmer
				} else {
					switch (taskStage) {
	            	case 0:
	            		pickFarmLocation();
	            		if (currentLocation.distanceTo(targetLocation) < 0.04f) {
	            			taskStage = 1;
	            			rc.broadcast(28, rc.readBroadcast(28) - 1);
	                    } else {
	                    	targetDirection = currentLocation.directionTo(targetLocation);
	                    	float targetDistance = currentLocation.distanceTo(targetLocation);
	            			if(targetDistance < type.strideRadius){
	            				tryMove(targetDirection, 10, 8, targetDistance);
	            			} else {
	            				tryMove(targetDirection, 10, 8, type.strideRadius);
	            			}
		            		break;
	                    }

//	            		if(atFarmLocation(currentLocation) && rc.senseNearbyTrees(3.1f).length < 2){
//	            			taskStage = 1;
//	            		} else {
//	            			System.out.println(targetLocation);
//	            			//Fix so they search through the different tiles
//	            			targetLocation = new MapLocation(currentLocation.x + currentLocation.x % 8, currentLocation.y + currentLocation.y % 8);
//	            			targetDirection = currentLocation.directionTo(targetLocation);
//	            			float targetDistance = currentLocation.distanceTo(targetLocation);
//	            			if(targetDistance < type.strideRadius){
//	            				tryMove(targetDirection, 20, 3, targetDistance);
//	            			} else {
//	            				tryMove(targetDirection);
//	            			}
//	            			System.out.println(targetLocation);
//	    					break;
//	            		}
	            	case 1:
	            		if(!gettingRushed){
		            		for (Direction plantDirection: plantDirections) {
		            			if (rc.canPlantTree(plantDirection)) {
		                            rc.plantTree(plantDirection);
		                            break;
		                		}
		            		}
	            		}
	            		TreeInfo[] trees = rc.senseNearbyTrees(2.1f);
	            		MapLocation waterLocation = null;
	            		float treeHealth = 0x40;
	            		for (TreeInfo tree : trees) {
	            			if (tree.getHealth() < treeHealth) {
	            				treeHealth = tree.getHealth();
	            				waterLocation = tree.getLocation();
	            			}
	            		}
	            		if (waterLocation != null && rc.canWater(waterLocation)) {
	            			rc.water(waterLocation);
	            		}
	            		break;
	            	}
				}
				Clock.yield();
			} catch (Exception e) {
				System.out.println("Gardener Exception");
				e.printStackTrace();
			}
		}
	}

	private static boolean atFarmLocation(MapLocation loc) {
		if((loc.x % 6) < (6 * 0.1f) && (loc.y % 6) < (6 * 0.1f)){
			return true;
		}
		return false;
	}
	
	private static void pickFarmLocation() {
		try{
    		float x_r = rc.readBroadcastFloat(998);
    		float x_l = rc.readBroadcastFloat(999);
    		float y_t =  rc.readBroadcastFloat(996);
    		float y_b = rc.readBroadcastFloat(997);
//    		System.out.println(x_r);
//	    	System.out.println(x_l);
//	    	System.out.println(y_t);
//	    	System.out.println(y_b);
//	    	System.out.println((x_r != -1f && targetLocation.x > x_r - 2));
//	    	System.out.println((x_r != -1f && targetLocation.x > x_r - 2));
//	    	System.out.println( (y_t != -1f && targetLocation.y > y_t - 2));
//	    	System.out.println((y_b != 10000f && targetLocation.y < y_b - 2));
//	    	System.out.println(targetLocation);
	    	if (freeSpace(targetLocation, 1f) > 0) {
	    		if((x_r != -1f && targetLocation.x > x_r - 2) || (x_l != 10000f && targetLocation.x < x_l - 2) 
	    				|| (y_t != -1f && targetLocation.y > y_t - 2)  || (y_b != 10000f && targetLocation.y < y_b - 2)){
	    			
	    		} else {
	    			System.out.println("return");
	    			return;
	    		}
//	    	} else if (freeSpace(targetLocation, 1f) == 2){
//	    		return;
	    	}
	    //	System.out.println("location: " + targetLocation);
	    	//boolean foundLocation = false;
			MapLocation location = new MapLocation(
		    		Math.round(rc.getLocation().x / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset,
		    		Math.round(rc.getLocation().y / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset
		    		);
			int counter = (int)(6 * (Math.random()));
		    for (int i = 0; i < 6; i++) {
		    	Direction direction  = farmDirections[counter];
		    	MapLocation newLocation = location.add(direction, farmerSpacing);
//		    	System.out.println("loc considered " + newLocation);
//		    	System.out.println(x_r);
//		    	System.out.println(x_l);
//		    	System.out.println(y_t);
//		    	System.out.println(y_b);
//		    	System.out.println((x_r != -1f && newLocation.x > x_r - 2));
//		    	System.out.println((x_r != -1f && newLocation.x > x_r - 2));
//		    	System.out.println( (y_t != -1f && newLocation.y > y_t - 2));
//		    	System.out.println((y_b != 10000f && newLocation.y < y_b - 2));
		    	if((x_r != -1f && newLocation.x > x_r - 2) || (x_r != -1f && newLocation.x > x_r - 2)
		    			|| (y_t != -1f && newLocation.y > y_t - 2)  || (y_b != 10000f && newLocation.y < y_b - 2)){
		    		continue;
		    	} else {
			    	if (freeSpace(newLocation, 1.0f) > 0) {
			    		targetLocation = newLocation;
			    		System.out.println("for " + targetLocation);
			    		return;
			    	}		   	
			   	}
		    	counter = ++counter % 6;
		    }
		   // targetLocation = currentLocation.add(new Direction(currentLocation, rc.getInitialArchonLocations(enemyTeam)[0]), 5f);
		    		
	    } catch (Exception e) {
				e.printStackTrace();
			}
	 }
	 
	static int freeSpace(MapLocation location, float radius) {
		 try {
			RobotInfo[] robots = rc.senseNearbyRobots();
		    TreeInfo[] trees = rc.senseNearbyTrees();
		    if (rc.canSenseAllOfCircle(location, radius)) {
		    	for (RobotInfo robot : robots) {
		    		if (robot.getLocation().distanceTo(location) < robot.getRadius() + radius) {
		    			return 0;
		    		}
		    	}
				for (TreeInfo tree : trees) {
		    		if (tree.getLocation().distanceTo(location) < tree.getRadius() + radius) {
		    			return 0;
		    		}
		    	}
	    	} else {
	    		return 2;
	    	}
		 } catch (Exception e) {
			 System.out.println("Soldier Exception");
			 e.printStackTrace();
		 }
		 return 1;
	}
		 

	@SuppressWarnings("static-access")
	private static void runSoldier() throws GameActionException {
		while (true) {
			try {
				clearEnemyArchonChannels();
				checkVictoryPoints();
				currentLocation = rc.getLocation();
				
				//Check for enemies and move accordingly
				enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
				if (enemyRobots.length > 0) {
					RobotInfo enemyRobot = enemyRobots[0];
		            targetLocation = enemyRobot.getLocation();
		            targetDirection = currentLocation.directionTo(targetLocation);
		    		
		            //Make sure no friendly bros or tree bros are in the line of fire
		            if(currentLocation.distanceTo(targetLocation) < 4f && enemyRobot.getType() == type.ARCHON || enemyRobot.getType() == type.GARDENER && rc.canFireTriadShot()){
		            	rc.fireTriadShot(currentLocation.directionTo(targetLocation));
		            } else if(noFriendlyFire() && noTreeFire() && rc.canFireTriadShot()){
						rc.fireTriadShot(currentLocation.directionTo(targetLocation));
					}
	                
	                if(enemyRobot.getType() != type.GARDENER && enemyRobot.getType() != type.ARCHON){
	                	rc.broadcastFloat(30, enemyRobot.getLocation().x);
						rc.broadcastFloat(31, enemyRobot.getLocation().y);
						rc.broadcast(32, rc.getRoundNum());
			            if(currentLocation.distanceTo(targetLocation) <= 4f){
			            	intelligentMove(targetDirection.opposite());
			            } else {
				            //So that this bro doesn't walk into his own shots
				            if(!rc.hasAttacked()){
				            	intelligentMove(targetDirection);
				            } else if((bullets = rc.senseNearbyBullets(8)).length > 0){
				            	tryMove(advancedBulletDodge(targetDirection, rc.senseNearbyBullets(8), 2));
				            }
			            }
	                } else{
	                	if(enemyRobot.getType() == type.ARCHON){
	                		compromiseArchon(enemyRobot);
	                	}
	                	if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
	                		bullets = rc.senseNearbyBullets(8);
	                		if(bullets.length > 0){
	                			tryMove(advancedBulletDodge(targetDirection, bullets, 2));
	                		}
			            } else if(!rc.hasAttacked()){
			            	intelligentMove(targetDirection);
					    } else if((bullets = rc.senseNearbyBullets(8)).length > 0){
					    	tryMove(advancedBulletDodge(targetDirection, rc.senseNearbyBullets(8), 2));
					    }
	                }
	                //No enemy found -> go to enemy archon locations
	             } else {
	            	 boolean hasTarget = false;
	            	 if(rc.getRoundNum() - rc.readBroadcast(32) < 15){
	            		 hasTarget = true;
	            		 targetLocation = new MapLocation(rc.readBroadcastFloat(30), rc.readBroadcastFloat(31));
	            		 targetDirection = currentLocation.directionTo(targetLocation);
	            	 } else {
		            	 for(int i = 11; i < 23; i += 4){
		            		 if(rc.readBroadcast(i) != 0){
		            			 targetDirection = currentLocation.directionTo(new MapLocation(rc.readBroadcastFloat(i + 1), rc.readBroadcastFloat(i + 2)));
		            			 hasTarget = true;
		            			 break;
		            		 }
		            	 }
	            	 }
	            	 if(hasTarget){
	            		 intelligentMove(targetDirection);
	            	 } else {
	            		 bounce();
	            		 intelligentMove(casualDirection);
	            	 }
	             }
				Clock.yield();
			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
		}
	}

	private static void runLumberjack() throws GameActionException {
		while (true) {
			 try {
				 checkVictoryPoints();
				 currentLocation = rc.getLocation();

				 enemyRobots = rc.senseNearbyRobots(3.5f, enemyTeam);
	             trees = rc.senseNearbyTrees(-1);
	             if (enemyRobots.length > 0) {
	            	 for(RobotInfo enemyRobot: enemyRobots){
			             targetLocation = enemyRobot.getLocation();
			             targetDirection = currentLocation.directionTo(targetLocation);
			             if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
			            	 tryMove(targetDirection, 20, 3, currentLocation.distanceTo(targetLocation) - type.bodyRadius - enemyRobot.getType().bodyRadius - 0.01f);
			             } else {
			            	 tryMove(targetDirection, 20, 3, type.strideRadius);
			             }
			             if (currentLocation.distanceTo(targetLocation) < GameConstants.LUMBERJACK_STRIKE_RADIUS + enemyRobot.getRadius() && rc.canStrike()) {
			            	 rc.strike();
			             }
	            	 }
	             } else if(trees.length > 0){
	            	 boolean hasTarget = false;
		             for(TreeInfo tree: trees){
			             if(!tree.getTeam().isPlayer()){
				             targetLocation = tree.getLocation();
				             targetDirection = currentLocation.directionTo(targetLocation);
				             if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
				            	 tryMove(targetDirection, 20, 3, currentLocation.distanceTo(targetLocation) - type.bodyRadius - tree.getRadius() - 0.01f);
				             } else {
				            	 tryMove(targetDirection, 20, 3, type.strideRadius);
				             }      
				             if (rc.canChop(tree.getID())) {
				            	 rc.chop(tree.getID());
				             }
				             hasTarget = true;
				             break;
			             }
		             }
		             if(!hasTarget){
			             bounce();
		            	 intelligentMove(casualDirection);
		             }
	             } else {
	            	 bounce();
	            	 intelligentMove(casualDirection);
	             }
	             Clock.yield();
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
		}
	}
	
	private static void runScout() throws GameActionException{
		while (true) {
			try {
				checkVictoryPoints();
				currentLocation = rc.getLocation();
				
				//Check for many trees
				if(rc.getRoundNum() < 50 && mappedTrees.size() < 50){
					mappedTrees.addAll(Arrays.asList(rc.senseNearbyTrees(-1)));
					
				}
				if(rc.getRoundNum() == 75){
					if(mappedTrees.size() > 30){
						rc.broadcast(9, rc.readBroadcast(9) + mappedTrees.size() / 12);
						
					}
				}
				
				//clear old enemy archon channels
				clearEnemyArchonChannels();
				
				hasEnemyTarget = huntEnemies(currentLocation);
				//Check for gardeners, archons and trees containing bullets
				if(!hasEnemyTarget){
					if(superTreeID == -1){
						//If no gardeners were found, look for trees containing bullets
						findTreeWithBullets();
						
						//if superIsSet = true look for the nearby superTreeID
					} else if((superTreeID != -1 && rc.canInteractWithTree(superTreeID))){
						if(rc.canShake() && rc.senseTree(superTreeID).getContainedBullets() > 0){
							rc.shake(superTreeID);
						}
						if(!findTreeWithBullets()){
							superTreeID = -1;
						}
					}
				}
				//Move regularly or to target
				if(!hasEnemyTarget && superTreeID == -1){
					//bounce
					bounce();
					intelligentMove(casualDirection);
				} else {
					tryMove(targetDirection, 20, 3, moveLength);
				}
				
				if(hasEnemyTarget && noFriendlyFire() && currentLocation.distanceTo(targetLocation) < 4f && rc.canFireSingleShot()){
					rc.fireSingleShot(targetDirection);
				}
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
		
	@SuppressWarnings("static-access")
	private static void runTank() throws GameActionException{
		while (true) {
			try {
				clearEnemyArchonChannels();
				checkVictoryPoints();
				currentLocation = rc.getLocation();
				
				//Check for enemies and move accordingly
				enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
				if (enemyRobots.length > 0) {
					RobotInfo enemyRobot = enemyRobots[0];
		            targetLocation = enemyRobot.getLocation();
		            targetDirection = currentLocation.directionTo(targetLocation);
		    		
		            //Make sure no friendly bros or tree bros are in the line of fire
		            if(noFriendlyFire() && noTreeFire() && rc.canFirePentadShot()){
						rc.firePentadShot(currentLocation.directionTo(targetLocation));
					} else if(noFriendlyFire() && noTreeFire() && rc.canFireSingleShot()){
						rc.fireSingleShot(currentLocation.directionTo(targetLocation));
					}
	                
	                if(enemyRobot.getType() != type.GARDENER && enemyRobot.getType() != type.ARCHON){
	                	rc.broadcastFloat(30, enemyRobot.getLocation().x);
						rc.broadcastFloat(31, enemyRobot.getLocation().y);
						rc.broadcast(32, rc.getRoundNum());
			            if(currentLocation.distanceTo(targetLocation) <= 4f){
			            	intelligentMove(targetDirection.opposite());
			            } else {
				            //So that this bro doesn't walk into his own shots
				            if(!rc.hasAttacked()){
				            	intelligentMove(targetDirection);
				            } else if((bullets = rc.senseNearbyBullets(-1)).length > 0){
				            	tryMove(advancedBulletDodge(targetDirection, rc.senseNearbyBullets(-1), 2));
				            }
			            }
	                } else{
	                	if(enemyRobot.getType() == type.ARCHON){
	                		compromiseArchon(enemyRobot);
	                	}
	                	if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
	                		bullets = rc.senseNearbyBullets(8);
	                		if(bullets.length > 0){
	                			tryMove(advancedBulletDodge(targetDirection, bullets, 2));
	                		}
			            } else if(!rc.hasAttacked()){
			            	intelligentMove(targetDirection);
					    } else if((bullets = rc.senseNearbyBullets(8)).length > 0){
					    	tryMove(advancedBulletDodge(targetDirection, rc.senseNearbyBullets(8), 2));
					    }
	                }
	                //No enemy found -> go to enemy archon locations
	             } else {
	            	 boolean hasTarget = false;
		             for(int i = 11; i < 23; i += 4){
		            	 if(rc.readBroadcast(i) != 0){
		            		 targetDirection = currentLocation.directionTo(new MapLocation(rc.readBroadcastFloat(i + 1), rc.readBroadcastFloat(i + 2)));
		            		 hasTarget = true;
		            		 break;
		            	 }
		             }
	            	 if(hasTarget){
	            		 intelligentMove(targetDirection);
	            	 } else {
	            		 bounce();
	            		 intelligentMove(casualDirection);
	            	 }
	            }
				Clock.yield();
			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
		}
	}
			
		
	
	

	//---------------------SCOUT METHODS---------------------//
	
	@SuppressWarnings("static-access")
	private static boolean huntEnemies(MapLocation currentLocation) throws GameActionException{
		enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
		for(RobotInfo enemyRobot: enemyRobots){
			if(enemyRobot.getType() == type.GARDENER){
				//hugTarget(enemyRobot);
				return false;
			} else if (enemyRobot.getType() == type.ARCHON) {
				compromiseArchon(enemyRobot);
				return false;
			}
		}
		return false;
	}
	
	private static void hugTarget(RobotInfo enemyRobot){
		targetLocation = enemyRobot.getLocation();
		targetDirection = new Direction(currentLocation, targetLocation);
		if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
			moveLength = currentLocation.distanceTo(targetLocation) - type.bodyRadius - enemyRobot.getType().bodyRadius - 0.01f;
		} else {
			moveLength = type.strideRadius;
		}
		superTreeID = -1;
	}
	
	private static boolean findTreeWithBullets(){
		try {
			trees = rc.senseNearbyTrees(-1);
			for(TreeInfo tree: trees){
				if(tree.getContainedBullets() > 0){
					int treeID = tree.getID();
					if(rc.canShake() && rc.canInteractWithTree(treeID)){
						rc.shake(treeID);
					} else {
						targetLocation = tree.getLocation();
						targetDirection = new Direction(currentLocation, targetLocation);
						superTreeID = treeID;
						moveLength = type.strideRadius;
					}
					return true;
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}	
		return false;
	}
	
	//---------------------END OF SCOUT METHODS---------------------//

	//---------------------MISC---------------------//
	
	private static void checkVictoryPoints(){
    	try {
    		int roundNum = rc.getRoundNum();
    		if (rc.getTeamBullets() >= 7500 + roundNum * 12.5 / 3000 * 1000 || roundNum > rc.getRoundLimit() - 30) {
    			rc.donate(rc.getTeamBullets());
    		}
		} catch (GameActionException e) {
			e.printStackTrace();
    	}
	}
	
	
	//Recks enemy Archons xD
	private static void compromiseArchon(RobotInfo enemyRobot){
		try {
			int archonID = enemyRobot.getID();
			int j = 10;
			while(archonID != rc.readBroadcast(j) && rc.readBroadcast(j) != 0){
				j += 4;
			}
			if(rc.readBroadcast(j) == 0){
				rc.broadcast(j, archonID);
			}
			
			rc.broadcast(j + 1, rc.getRoundNum());
			rc.broadcastFloat(j + 2, enemyRobot.getLocation().x);
			rc.broadcastFloat(j + 3, enemyRobot.getLocation().y);
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	static void clearEnemyArchonChannels(){
		try {
			for(int i = 11; i < 23; i += 4){
				if(rc.readBroadcast(i) != 0 && rc.getRoundNum() - rc.readBroadcast(i) > ARCHON_TIMEOUT){
					rc.broadcast(i - 1, 0);
					rc.broadcast(i, 0);
					rc.broadcast(i + 1, 0);
					rc.broadcast(i + 2, 0);
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	//---------------------END MISC---------------------//
	
	//---------------------BUILDING---------------------//
	
	private static boolean tryBuildRobot(RobotType type) throws GameActionException {
		if (rc.getTeamBullets() > type.bulletCost + BULLET_MARGIN + rc.getRoundNum() / 25) {
			for (int i = 0; i < dirList.length; i++) {
				if (rc.canBuildRobot(type, dirList[i])) {
					rc.buildRobot(type, dirList[i]);
					return true;
				}
			}
		}
		return false;
	}
	
	//---------------------END BUILDING---------------------//

	//---------------------MOVEMENT---------------------//	
	
	private static void scoutBounce(){
		try {
			if (!rc.canMove(casualDirection)) {
				float dx = casualDirection.getDeltaX(2.5f);
				if (!rc.canMove(new Direction(dx, 0), abs(dx))) {
					float x_r = rc.readBroadcastFloat(998);
					float x_l = rc.readBroadcastFloat(999);
					MapLocation testWith = currentLocation.add(casualDirection, 4f);
					if(!rc.onTheMap(testWith) &&
							((testWith.x > x_r && x_r == -1) ||
							(testWith.x < x_l && x_l == 10000))){
						int modifier = 0;
						if(x_r == -1 && testWith.x > x_r){
							modifier = 1;
						}
						MapLocation loc = currentLocation;
						for(int i = 1; i < 75; i++){
							loc = loc.add(modifier*PI, 0.05f);
							if(!rc.onTheMap(loc)){
								if(modifier == 1){
									rc.broadcastFloat(998, loc.x);
								} else {
									rc.broadcastFloat(999, loc.x);
								}
								break;
							}
						}
		    		}
					if (casualDirection.radians > 0) {
						casualDirection = new Direction(PI - casualDirection.radians);
					} else {
						casualDirection = new Direction(-PI + casualDirection.radians);
					}
				}
				if (!rc.canMove(new Direction(0,  casualDirection.getDeltaY(2.5f)), abs(casualDirection.getDeltaY(2.5f)))) {
					float y_r = rc.readBroadcastFloat(996);
					float y_l = rc.readBroadcastFloat(997);
					MapLocation testWith = currentLocation.add(casualDirection, 4f);
					if(!rc.onTheMap(testWith) &&
							((testWith.y > y_r && y_r == -1) ||
							(testWith.y < y_l && y_l == 10000))){
						int modifier = 3;
						if(y_r == -1 && testWith.x > y_r){
							modifier = 1;
						}
						MapLocation loc = currentLocation;
						for(int i = 1; i < 75; i++){
							loc = loc.add(modifier*PI/2, 0.05f);
							if(!rc.onTheMap(loc)){
								if(modifier == 1){
									rc.broadcastFloat(996, loc.y);
								} else {
									rc.broadcastFloat(997, loc.y);
								}
								break;
							}
						}
		    		}
					casualDirection = new Direction(casualDirection.radians * -1);
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}	
	}
	
	private static void bounce(){
			if (!rc.canMove(casualDirection)) {
				float dx = casualDirection.getDeltaX(2.5f);
				if (!rc.canMove(new Direction(dx, 0), abs(dx))) {
					
					if (casualDirection.radians > 0) {
						casualDirection = new Direction(PI - casualDirection.radians);
					} else {
						casualDirection = new Direction(-PI + casualDirection.radians);
					}
				}
				float dy = casualDirection.getDeltaY(2.5f);
				if (!rc.canMove(new Direction(0, dy), abs(dy))) {
					casualDirection = new Direction(casualDirection.radians * -1);
				}
			}
	}
	
	private static Direction randomDirection() {
		return new Direction((float) random() * 2 * PI);
	}

	private static void intelligentMove(Direction dir) {
		try {
			bullets = rc.senseNearbyBullets(8);
			if (bullets.length > 0) {
				dir = advancedBulletDodge(dir, bullets, 0);
			}
			tryMove(dir, 15, 5, type.strideRadius);
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	private static void intelligentMove(MapLocation loc) {
			intelligentMove(currentLocation.directionTo(loc));
	}

	//No bulletdodge here, so that they don't run into their own shots
	private static void intelligentMove(Direction dir, float moveLength){
		try {
			bullets = rc.senseNearbyBullets(8);
			if (bullets.length > 0) {
				tryMove(advancedBulletDodge(dir, bullets, 0), 20, 3, type.strideRadius);
			} else {
				tryMove(dir, 15, 5, moveLength);
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}

	private static boolean tryMove(Direction dir) throws GameActionException {
		if (rc.canMove(dir)) {
			rc.move(dir);
			return true;
		}
		return false;
	}

	private static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide, float moveLength) throws GameActionException {

		// First, try intended direction
		if (rc.canMove(dir, moveLength)) {
			rc.move(dir, moveLength);
			return true;
		}

		// Now try a bunch of similar angles
		int currentCheck = 1;

		while (currentCheck <= checksPerSide) {
			// Try the offset of the left side
			if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck), moveLength)) {
				rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck), moveLength);
				return true;
			}
			// Try the offset on the right side
			if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck), moveLength)) {
				rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck), moveLength);
				return true;
			}
			// No move performed, try slightly further
			currentCheck++;
		}

		// A move never happened, so return false.
		return false;
	}
	
	//---------------------END MOVEMENT---------------------//
	
	//---------------------BULLET DODGING---------------------//
	
	/*
	 * Don't use this shiet for scout, they have to great bullet detection and will just act weird
	 * checks  x x x
	 * 		  x -O- x
	 *         x   x, picks the location of the 7 that is safe
	 */
	private static Direction advancedBulletDodge(Direction dir, BulletInfo[] bullets, int goForward) {
		Direction potentialDirection = dir;
		TreeMap<Integer, Direction> weights = new TreeMap<>();
		int weight;
		for(int i = goForward; i < 4; i = i > 0 ? i*-1 : --i*-1){
			weight = 0;
			potentialDirection = dir.rotateLeftRads(i*PI/4);
			if(!rc.canMove(potentialDirection)){
				continue;
			}
			for(BulletInfo bullet: bullets){
				MapLocation potentialLoc = currentLocation.add(potentialDirection, type.strideRadius);
				Direction bulletDir = bullet.getDir();
				MapLocation bulletLoc = bullet.getLocation();
	
				Direction fromBulletDir = bulletLoc.directionTo(potentialLoc);
				float distance = bulletLoc.distanceTo(potentialLoc);
				float hitAngle = bulletDir.radiansBetween(fromBulletDir);
	
				if (abs(hitAngle) > PI / 2) {
					continue;
				}
	
				float perpendicularDistance = (float) abs(distance * sin(hitAngle));
				if (perpendicularDistance <= type.bodyRadius) {
					if(potentialLoc.distanceTo(bulletLoc) < 4){
						weight += 2;
					} else {
						weight++;
					}
				}
			}
			if(weight == 0){
				return potentialDirection;
			}
			weights.put(weight, potentialDirection);
		}
		if(!weights.isEmpty()){
			return weights.pollFirstEntry().getValue();
		}
		return dir;
	}

	private static Direction bulletDodge(Direction dir, BulletInfo[] bullets) {
		for (BulletInfo bullet: bullets) {
			Direction bDir = bullet.getDir();
			MapLocation bLoc = bullet.getLocation();

			Direction fromBulletDir = bLoc.directionTo(currentLocation);
			float distance = bLoc.distanceTo(currentLocation);
			float hitAngle = bDir.radiansBetween(fromBulletDir);
			if (abs(hitAngle) > PI / 2) {
				continue;
			}

			float perpendicularDistance = (float) abs(distance * sin(hitAngle));

			// if true then bullet will collide -> we must dodge
			if (perpendicularDistance <= type.bodyRadius) {
				Direction newDir = bullet.getDir().rotateLeftRads(PI / 2);
				if (rc.canMove(newDir)) {
					return newDir;
				} else if (rc.canMove(newDir.rotateLeftRads(PI))) {
					return newDir.rotateLeftRads(PI);
				}
			}
		}
		return dir;
	}
	
	//---------------------DODGING---------------------//
	
	//---------------------SHOOTING---------------------//
	
	//Not 100 % this works, but I think so
	private static boolean noFriendlyFire(){
        friendlyRobots = rc.senseNearbyRobots(4, rc.getTeam());
		for(RobotInfo friendlyRobot: friendlyRobots){
			MapLocation friendlyLocation = friendlyRobot.getLocation();
			float distanceToFriendly = currentLocation.distanceTo(friendlyLocation);
			float criticalAngle = abs(currentLocation.directionTo(friendlyLocation).radiansBetween(targetDirection));
			float criticalDistance = (float) (distanceToFriendly * sin(criticalAngle));
			if(criticalDistance < friendlyRobot.getRadius() + 0.05f && distanceToFriendly < currentLocation.distanceTo(targetLocation)){
				return false;
			}
		}
		return true;
	}
	
	private static boolean noTreeFire(){
        trees = rc.senseNearbyTrees(6);
		for(TreeInfo tree: trees){
			if(tree.getTeam() != enemyTeam){
				MapLocation treeLocation = tree.getLocation();
				float distanceToTree = currentLocation.distanceTo(treeLocation);
				float criticalAngle = abs(currentLocation.directionTo(treeLocation).radiansBetween(targetDirection));
				float criticalDistance = (float) (distanceToTree * sin(criticalAngle));
				if(criticalDistance < tree.getRadius() + 0.02f && distanceToTree < currentLocation.distanceTo(targetLocation)){
					return false;
				}
			}
		}
		return true;
	}
	
	//---------------------END SHOOTING---------------------//
	
	//---------------------SORTING---------------------//

//	private static TreeMap<Float, BodyInfo> sortByDistance(BodyInfo[] bodies) {
//		TreeMap<Float, BodyInfo> bodyMap = new TreeMap<>();
//		for (BodyInfo body : bodies) {
//			bodyMap.put(rc.getLocation().distanceTo(body.getLocation()), body);
//		}
//		return bodyMap;
//	}
	
	//---------------------END SORTING---------------------//
}
