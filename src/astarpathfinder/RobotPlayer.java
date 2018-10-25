package astarpathfinder;

import battlecode.common.*;
import static java.lang.Math.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

public class RobotPlayer {
	static Team enemyTeam = Team.B;
	static RobotController rc;
	static ArrayList<TreeInfo> trees = new ArrayList<>();
	static TreeInfo nearbyTrees[];

	static final float ROTATION_MARGIN = 0.1f;

	public static void run(RobotController rcIn) throws GameActionException {
		rc = rcIn;
		if (rc.getTeam() == Team.B) {
			enemyTeam = Team.A;
		}
		switch (rc.getType()) {
		case ARCHON:
			runArchon();
			break;
		case GARDENER:
			runGardener();
			break;
		case SOLDIER:
			runSoldier();
			break;
		case LUMBERJACK:
			runLumberjack();
			break;
		}
	}

	private static void runArchon() throws GameActionException {
		rc.hireGardener(new Direction(0f));
		while (true) {
			// try {
			// TreeInfo[] ti = rc.senseNearbyTrees(4);
			// pathfind(new MapLocation(25f, 5f), ti);
			Clock.yield();
			// } catch (GameActionException e) {
			// System.out.println("Archon Exception");
			// e.printStackTrace();
			// }
		}
	}

	private static void runGardener() throws GameActionException {
		while (true) {
			try {
				Direction dir = new Direction(rc.getLocation(), new MapLocation(14.5f, 13f));
				if (!tryMove(dir)) {
					curveAround(new MapLocation(14.5f, 13f), true);
				}
				Clock.yield();
			} catch (GameActionException e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
	}

	private static void runSoldier() throws GameActionException {
		while (true) {

		}
	}

	private static void runLumberjack() throws GameActionException {
		while (true) {

		}
	}

	private static void curveAround(MapLocation finalLoc, boolean isClockwise) throws GameActionException {
		BodyInfo[] bodies = rc.senseNearbyTrees(rc.getType().bodyRadius + rc.getType().strideRadius);
		System.out.println("scan with: " + rc.getType().bodyRadius + rc.getType().strideRadius);
		TreeMap<Float, BodyInfo> tm = sortByDistance(bodies);
		MapLocation currentLocation = rc.getLocation();
		Direction dir = new Direction(currentLocation, finalLoc);
		for (int i = 0; i < tm.size(); i++) {
			BodyInfo body = tm.pollFirstEntry().getValue();
			System.out.println(body);
			MapLocation bodyLoc = body.getLocation();
			Direction bodyDir = rc.getLocation().directionTo(bodyLoc);
			System.out.println("treeDir\n" + bodyDir);
			rc.setIndicatorDot(bodyLoc, 255, 0, 0);
			float bodyDistance = rc.getLocation().distanceTo(bodyLoc);
			// System.out.println("treeDistance " + treeDistance);
			float bodyRadius = body.getRadius();
			// System.out.println("treeRadius " + bodyRadius);
			float thisRadius = rc.getType().bodyRadius;
			// System.out.println("robotRadius " + thisRadius);

			float extendedRadius = bodyRadius + thisRadius;
			// System.out.println("extendedRadius" + extendedRadius);

			float rotationRadians = (float) asin(extendedRadius / bodyDistance) + ROTATION_MARGIN;
			System.out.println("rotationRadians: " + rotationRadians);
			// This check may not be sufficient but probably works
			if (abs(dir.radiansBetween(bodyDir)) <= abs(
					bodyDir.radiansBetween(bodyDir.rotateLeftRads(rotationRadians)))) {
				if (isClockwise) {
					dir = bodyDir.rotateLeftRads(rotationRadians);
				} else {
					dir = bodyDir.rotateRightRads(rotationRadians);
				}
				rc.move(dir); // SHOULD MAYBE BE TRYMOVE; BUT IT SHOULD WORK
								// LIEK ALL DE TIME LULZ :P
			}
		}
	}

	private static TreeMap<Float, BodyInfo> sortByDistance(BodyInfo[] ti) {
		TreeMap<Float, BodyInfo> tm = new TreeMap<>();
		for (BodyInfo bi : ti) {
			tm.put(rc.getLocation().distanceTo(bi.getLocation()), bi);
		}
		return tm;
	}

	private static boolean tryMove(Direction dir) throws GameActionException {
		if (rc.canMove(dir)) {
			rc.move(dir);
			return true;
		}
		return false;
	}

	/*
	 * private static boolean tryMove(Direction dir) throws GameActionException
	 * { return tryMove(dir, 20, 3); }
	 * 
	 * private static boolean tryMove(Direction dir, float degreeOffset, int
	 * checksPerSide) throws GameActionException {
	 * 
	 * // First, try intended direction if (rc.canMove(dir)) { rc.move(dir);
	 * return true; }
	 * 
	 * // Now try a bunch of similar angles boolean moved = false; int
	 * currentCheck = 1;
	 * 
	 * while (currentCheck <= checksPerSide) { // Try the offset of the left
	 * side if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck)))
	 * { rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck)); return
	 * true; } // Try the offset on the right side if
	 * (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck))) {
	 * rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck)); return
	 * true; } // No move performed, try slightly further currentCheck++; }
	 * 
	 * // A move never happened, so return false. return false; }
	 */
}
